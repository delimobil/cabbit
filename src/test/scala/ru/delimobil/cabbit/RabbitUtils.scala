package ru.delimobil.cabbit

import java.util.UUID

import cats.Parallel
import cats.effect.ConcurrentEffect
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.syntax.all._
import cats.syntax.all._
import com.rabbitmq.client.AMQP.Queue
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

final class RabbitUtils[F[_]: ConcurrentEffect: Parallel: Timer](val ch: Channel[F]) {

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

  def useWithDeadQueue(
    maxLength: Option[Int]
  )(testFunc: (QueueDeclaration, QueueDeclaration, BindDeclaration) => F[Unit]): Unit =
    declareAcquire(Map.empty)
      .bracket { case (deadQueue, deadBind) =>
        val args = Map("x-dead-letter-exchange" -> deadBind.exchangeName.name) ++ maxLength.map("x-max-length" -> _)
        useRandomlyDeclaredIO(args) { case (queue, bind) => testFunc(deadQueue, queue, bind) }
      } (res => declareRelease(res._2))
      .toIO
      .unsafeRunSync()

  def useWithAE(
    rk: RoutingKey
  )(testFunc: (ExchangeDeclaration, QueueDeclaration, QueueDeclaration) => F[Unit]): Unit =
    (uuidIO, uuidIO, uuidIO)
      .tupled
      .flatMap { case (uuid1, uuid2, uuid3) =>
        val aeExchange = fanout(uuid1)
        val aeQueue = getQueue(uuid2, Map.empty)
        val aeBind = BindDeclaration(aeQueue.queueName, aeExchange.exchangeName, RoutingKeyDefault)
        val aeDec = ch.exchangeDeclare(aeExchange) *> ch.queueDeclare(aeQueue) *> ch.queueBind(aeBind)

        val topicExchange = topic(uuid3, Map("alternate-exchange" -> aeExchange.exchangeName.name))
        val topicDec = ch.exchangeDeclare(topicExchange) *> addBind(topicExchange.exchangeName, rk)

        aeDec *> topicDec.map { case(queue, bind) => (topicExchange, queue, aeQueue, aeBind, bind) }
      }
      .bracket { case (ex, q1, q2, _, _) =>
        testFunc(ex, q1, q2)
      } (res => declareRelease(res._4) *> declareRelease(res._5))
      .toIO
      .unsafeRunSync()

  def addBind(topic: ExchangeName, rk: RoutingKey): F[(QueueDeclaration, BindDeclaration)] =
    uuidIO
      .flatMap { uuid =>
        val topicQueue = getQueue(uuid, Map.empty)
        val topicBind = BindDeclaration(topicQueue.queueName, topic, rk)
        ch.queueDeclare(topicQueue) *> ch.queueBind(topicBind).as((topicQueue, topicBind))
      }

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

object RabbitUtils {

  def make[F[_]: ConcurrentEffect: Parallel: Timer](conn: Connection[F]): Resource[F, RabbitUtils[F]] =
    conn.createChannel.map(ch => new RabbitUtils(ch))
}
