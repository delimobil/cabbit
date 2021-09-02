package ru.delimobil.cabbit

import java.util.UUID

import cats.Parallel
import cats.effect.ConcurrentEffect
import cats.effect.Timer
import cats.effect.syntax.all._
import cats.syntax.all._
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Delivery
import fs2.Stream
import io.circe.Json
import io.circe.parser.parse
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument
import ru.delimobil.cabbit.algebra.ContentEncoding.decodeUtf8
import ru.delimobil.cabbit.algebra.ContentEncoding.ungzip
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.config.declaration.Arguments
import ru.delimobil.cabbit.config.declaration.BindDeclaration
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.reflect.ClassTag

final class RabbitUtils[F[_]: ConcurrentEffect: Parallel: Timer](conn: Connection[F], ch: Channel[F]) {

  import ru.delimobil.cabbit.algebra.BodyEncoder.instances.jsonGzip

  private val utils = new DeclareUtils[F]

  import utils._

  def readAck(
    tuple: (ConsumerTag, Stream[F, Delivery]),
    timeout: FiniteDuration = 100.millis
  ): F[List[Either[Exception, Json]]] =
    tuple
      ._2
      .evalTap(d => ch.basicAck(DeliveryTag(d.getEnvelope.getDeliveryTag), multiple = false))
      .concurrently(Stream.eval_(Timer[F].sleep(timeout) *> ch.basicCancel(tuple._1)))
      .compile
      .toList
      .map(_.map(d => ungzip(d.getBody).map(decodeUtf8).flatMap(parse)))

  def readAll(queue: QueueName, timeout: FiniteDuration = 100.millis): F[List[Either[Exception, Json]]] =
    ch.deliveryStream(queue, 100).flatMap(readAck(_, timeout))

  def publish(messages: List[String], bind: BindDeclaration): F[Unit] =
    messages.traverse_(publishOne(bind.exchangeName, bind.routingKey, _))

  def publishOne(exchange: ExchangeName, key: RoutingKey, msg: String): F[Unit] =
    ch.basicPublish(exchange, key, msg, mandatory = MandatoryArgument.Mandatory)

  def declareAcquire(qProps: Arguments): F[(QueueDeclaration, BindDeclaration)] =
    uuidIO.flatMap { uuid =>
      val (exchange, queue, bind) = getDirectNonExclusive(uuid, qProps)
      ch.exchangeDeclare(exchange) *> ch.queueDeclare(queue) *> ch.queueBind(bind).as((queue, bind))
    }

  def declareRelease(bind: BindDeclaration): F[Unit] =
    ch.exchangeDelete(bind.exchangeName) <* ch.queueDelete(bind.queueName)

  def useRandomlyDeclaredIO(qProps: Arguments)(testFunc: (QueueDeclaration, BindDeclaration) => F[Unit]): F[Unit] =
    declareAcquire(qProps).bracket { case (queue, bind) => testFunc(queue, bind) } (res => declareRelease(res._2))

  def useRandomlyDeclared(qProps: Arguments)(testFunc: (QueueDeclaration, BindDeclaration) => F[Unit]): Unit =
    useRandomlyDeclaredIO(qProps)(testFunc).toIO.unsafeRunSync()

  private def rndEx: F[ExchangeName] = uuidIO.map(uuid => ExchangeName(uuid.toString))

  def bindQueueToExchangeIO(exName: ExchangeName, rk: RoutingKey, qProps: Arguments): F[BindDeclaration] =
    ch.queueDeclare(QueueDeclaration(QueueNameDefault, arguments = qProps))
      .flatMap { ok =>
        val bind = BindDeclaration(QueueName(ok.getQueue), exName, rk)
        ch.queueBind(bind).as(bind)
      }

  // rndExchange + autonameQueue(props) + FANOUT
  def bindedIO(qProps: Arguments): F[BindDeclaration] =
    rndEx.flatMap { exName =>
      ch.exchangeDeclare(ExchangeDeclaration(exName, BuiltinExchangeType.FANOUT))
        .productR(bindQueueToExchangeIO(exName, RoutingKeyDefault, qProps))
    }

  def useBinded(qProps: Arguments)(testFunc: BindDeclaration => F[Unit]): Unit =
    bindedIO(qProps).flatMap(testFunc).toIO.unsafeRunSync()

  def queueDeclaredIO(qProps: Arguments): F[QueueName] =
    ch.queueDeclare(QueueDeclaration(QueueNameDefault, arguments = qProps)).map(ok => QueueName(ok.getQueue))

  def useQueueDeclared(qProps: Arguments)(testFunc: QueueName => F[Unit]): Unit =
    queueDeclaredIO(qProps).flatMap(testFunc).toIO.unsafeRunSync()

  def alternateExchangeIO(rk: RoutingKey): F[(ExchangeName, QueueName, QueueName)] =
    for {
      alternateBind <- bindedIO(Map.empty)
      args = Map("alternate-exchange" -> alternateBind.exchangeName.name)
      primaryEx <- rndEx
      _ <- ch.exchangeDeclare(ExchangeDeclaration(primaryEx, BuiltinExchangeType.TOPIC, arguments = args))
      primaryBind <- bindQueueToExchangeIO(primaryEx, rk, Map.empty)
    } yield (primaryEx, primaryBind.queueName, alternateBind.queueName)

  def useAlternateExchange(rk: RoutingKey)(testFunc: (ExchangeName, QueueName, QueueName) => F[Unit]): Unit =
    alternateExchangeIO(rk).flatMap(testFunc.tupled).toIO.unsafeRunSync()

  def spoilChannel[Thr <: Throwable](f: Channel[F] => F[Unit])(implicit classTag: ClassTag[Thr]): Unit =
    conn
      .createChannel
      .use { ch =>
        f(ch).recoverWith { case ex =>
          ch.isOpen.map { open =>
            assert(classTag.runtimeClass.isAssignableFrom(ex.getClass), "Thr class is wrong")
            assert(!open)
          }
        }
      }
      .toIO
      .unsafeRunSync()

  def declareExclusive(
    channel1: ChannelDeclaration[F],
    channel2: ChannelDeclaration[F]
  ): F[(Either[Throwable, Queue.DeclareOk], Either[Throwable, Queue.DeclareOk])] =
    uuidIO
      .flatMap { uuid =>
        val queue = getQueue(uuid)
        (channel1.queueDeclare(queue).attempt, channel2.queueDeclare(queue).attempt).parTupled
      }

  private def getDirectNonExclusive(
    uuid: UUID,
    qProps: Arguments,
  ): (ExchangeDeclaration, QueueDeclaration, BindDeclaration) = {
    val exchange = direct(uuid)
    val queue = getQueue(uuid, qProps)
    val binding = BindDeclaration(queue.queueName, exchange.exchangeName, RoutingKey("the-key"))

    (exchange, queue, binding)
  }
}
