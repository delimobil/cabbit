package ru.delimobil.cabbit

import java.util.UUID

import cats.MonadError
import cats.Parallel
import cats.effect.ConcurrentEffect
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.syntax.all._
import cats.syntax.all._
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Delivery
import fs2.Stream
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument
import ru.delimobil.cabbit.algebra.ContentEncoding.decodeUtf8
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.config.declaration.Arguments
import ru.delimobil.cabbit.config.declaration.BindDeclaration
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.reflect.ClassTag

final class RabbitUtils[F[_]: ConcurrentEffect: Parallel: Timer](conn: Connection[F], ch: Channel[F]) {

  import ru.delimobil.cabbit.algebra.BodyEncoder.instances.textUtf8

  private val uuidIO: F[UUID] = Sync[F].delay(UUID.randomUUID())
  private val rndEx: F[ExchangeName] = uuidIO.map(uuid => ExchangeName(uuid.toString))
  private val rndQu: F[QueueName] = uuidIO.map(uuid => QueueName(uuid.toString))

  def readAck(
    tuple: (ConsumerTag, Stream[F, Delivery]),
    timeout: FiniteDuration = 100.millis
  ): F[List[String]] =
    tuple
      ._2
      .evalTap(d => ch.basicAck(DeliveryTag(d.getEnvelope.getDeliveryTag), multiple = false))
      .concurrently(Stream.eval_(Timer[F].sleep(timeout) *> ch.basicCancel(tuple._1)))
      .compile
      .toList
      .map(_.map(d => decodeUtf8(d.getBody)))

  def readAll(queue: QueueName, timeout: FiniteDuration = 100.millis): F[List[String]] =
    ch.deliveryStream(queue, 100).flatMap(readAck(_, timeout))

  def publish(messages: List[String], bind: BindDeclaration): F[Unit] =
    messages.traverse_(publishOne(bind.exchangeName, bind.routingKey, _))

  def publishOne(exchange: ExchangeName, key: RoutingKey, msg: String): F[Unit] =
    ch.basicPublish(exchange, key, msg, mandatory = MandatoryArgument.Mandatory)

  def declareRelease(bind: BindDeclaration): F[Unit] =
    ch.exchangeDelete(bind.exchangeName) <* ch.queueDelete(bind.queueName)

  def bindQueueToExchangeIO(exName: ExchangeName, rk: RoutingKey, qProps: Arguments): F[BindDeclaration] =
    for {
      qName <- rndQu
      _ <- ch.queueDeclare(getQueue2(qName, qProps))
      bind = BindDeclaration(qName, exName, rk)
      _ <- ch.queueBind(bind)
    } yield bind

  def getQueue2(qName: QueueName, qProps: Arguments): QueueDeclaration =
    QueueDeclaration(qName, arguments = qProps)

  // rndExchange + autonameQueue(props) + FANOUT
  def bindedIO(qProps: Arguments): F[BindDeclaration] =
    rndEx.flatMap { exName =>
      ch.exchangeDeclare(ExchangeDeclaration(exName, BuiltinExchangeType.FANOUT))
        .productR(bindQueueToExchangeIO(exName, RoutingKeyDefault, qProps))
    }

  def useBinded(qProps: Arguments)(testFunc: BindDeclaration => F[Unit]): Unit =
    bindedIO(qProps).flatMap(testFunc).toIO.unsafeRunSync()

  // Returns QueueName, because queueDeclare(QueueDeclaration) would throw on auto assigned name queues.
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

  def spoilChannel[E <: Throwable](f: Channel[F] => F[Unit])(implicit classTag: ClassTag[E]): Unit =
    conn
      .createChannel
      .use { ch =>
        f(ch)
          .attempt
          .flatMap {
            case Left(ex) =>
              ch.isOpen.map { open =>
                assert(classTag.runtimeClass.isAssignableFrom(ex.getClass), "E class is wrong")
                assert(!open)
              }
            case Right(()) =>
              val ex = new java.lang.AssertionError("assertion failed: expected error, found no one")
              MonadError[F, Throwable].raiseError[Unit](ex)
          }
      }
      .toIO
      .unsafeRunSync()

  def declareExclusive(
    channel1: ChannelDeclaration[F],
    channel2: ChannelDeclaration[F]
  ): F[(Either[Throwable, Queue.DeclareOk], Either[Throwable, Queue.DeclareOk])] =
    uuidIO.flatMap { uuid =>
      val queue = QueueDeclaration(QueueName(uuid.toString))
      (channel1.queueDeclare(queue).attempt, channel2.queueDeclare(queue).attempt).parTupled
    }
}
