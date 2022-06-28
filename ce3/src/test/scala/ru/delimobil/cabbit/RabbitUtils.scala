package ru.delimobil.cabbit

import cats.MonadError
import cats.effect.Sync
import cats.effect.instances.spawn._
import cats.effect.kernel.Async
import cats.effect.kernel.Temporal
import cats.effect.std.Dispatcher
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Delivery
import fs2.Stream
import ru.delimobil.cabbit.RabbitUtils._
import ru.delimobil.cabbit.api._
import ru.delimobil.cabbit.model.ConsumerTag
import ru.delimobil.cabbit.model.ContentEncoding.decodeUtf8
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.QueueName
import ru.delimobil.cabbit.model.RoutingKey
import ru.delimobil.cabbit.model.declaration.Arguments
import ru.delimobil.cabbit.model.declaration.AutoDeleteConfig
import ru.delimobil.cabbit.model.declaration.BindDeclaration
import ru.delimobil.cabbit.model.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.model.declaration.QueueDeclaration
import ru.delimobil.cabbit.syntax._

import java.util.UUID
import scala.concurrent.duration._
import scala.reflect.ClassTag

final class RabbitUtils[F[_]: Async](
    conn: Connection[F],
    ch: Channel[F],
    dispatcher: Dispatcher[F]
) {

  private val uuidIO: F[UUID] = Sync[F].delay(UUID.randomUUID())
  private val rndEx: F[ExchangeName] = uuidIO.map(uuid => ExchangeName(uuid.toString))
  private val rndQu: F[QueueName] = uuidIO.map(uuid => QueueName(uuid.toString))

  def bindQueueToExchangeIO(
      exName: ExchangeName,
      rk: RoutingKey,
      qProps: Arguments
  ): F[BindDeclaration] =
    for {
      qName <- rndQu
      _ <- ch.queueDeclare(QueueDeclaration(qName, arguments = qProps))
      bind = BindDeclaration(qName, exName, rk)
      _ <- ch.queueBind(bind)
    } yield bind

  // rndExchange + autonameQueue(props) + FANOUT
  def bindedIO(qProps: Arguments): F[BindDeclaration] =
    rndEx.flatMap { exName =>
      val exchange = ch.exchangeDeclare(ExchangeDeclaration(exName, BuiltinExchangeType.FANOUT))
      exchange *> bindQueueToExchangeIO(exName, RoutingKey.default, qProps)
    }

  def timedRead(
      tuple: (ConsumerTag, Stream[F, Delivery]),
      halt: F[Unit] = Temporal[F].sleep(150.millis),
      ack: Delivery => F[Unit] = d => ch.basicAck(d.deliveryTag, multiple = false)
  ): F[List[String]] = {
    val background = Stream.exec(halt *> ch.basicCancel(tuple._1))
    tuple._2
      .evalTap(ack)
      .concurrently(background)
      .compile
      .toList
      .map(_.map(d => decodeUtf8(d.getBody)))
  }

  def readAck(queue: QueueName, halt: F[Unit] = Temporal[F].sleep(150.millis)): F[List[String]] =
    ch.deliveryStream(queue, 100).flatMap { tuple =>
      timedRead(tuple, halt)
    }

  def readReject(queue: QueueName, halt: F[Unit] = Temporal[F].sleep(150.millis)): F[List[String]] =
    ch.deliveryStream(queue, 100).flatMap { tuple =>
      timedRead(tuple, halt, d => ch.basicReject(d.deliveryTag, requeue = false))
    }

  def useBinded(qProps: Arguments)(testFunc: BindDeclaration => F[Unit]): Unit =
    dispatcher.unsafeRunSync(bindedIO(qProps).flatMap(testFunc))

  // Returns QueueName, because queueDeclare(QueueDeclaration) would throw on auto assigned name queues.
  def queueDeclaredIO(qProps: Arguments): F[QueueName] = {
    val dec = QueueDeclaration(
      QueueName.default,
      autoDelete = AutoDeleteConfig.NonAutoDelete,
      arguments = qProps
    )
    ch.queueDeclare(dec).map(ok => QueueName(ok.getQueue))
  }

  def useQueueDeclared(qProps: Arguments)(testFunc: QueueName => F[Unit]): Unit =
    dispatcher.unsafeRunSync(queueDeclaredIO(qProps).flatMap(testFunc))

  def alternateExchangeIO(rk: RoutingKey): F[(ExchangeName, QueueName, QueueName)] =
    for {
      alternateBind <- bindedIO(Map.empty)
      args = Map("alternate-exchange" -> alternateBind.exchangeName.name)
      primaryEx <- rndEx
      _ <- ch.exchangeDeclare(
        ExchangeDeclaration(primaryEx, BuiltinExchangeType.TOPIC, arguments = args)
      )
      primaryBind <- bindQueueToExchangeIO(primaryEx, rk, Map.empty)
    } yield (primaryEx, primaryBind.queueName, alternateBind.queueName)

  def useAlternateExchange(rk: RoutingKey)(
      testFunc: (ExchangeName, QueueName, QueueName) => F[Unit]
  ): Unit =
    dispatcher.unsafeRunSync(alternateExchangeIO(rk).flatMap(testFunc.tupled))

  def spoilChannel[E <: Throwable](
      f: Channel[F] => F[Unit]
  )(implicit classTag: ClassTag[E]): Unit =
    dispatcher.unsafeRunSync(conn.createChannel.use(ch => validateError(f, ch)))

  def declareExclusive(
      channel1: ChannelDeclaration[F],
      channel2: ChannelDeclaration[F]
  ): F[(Either[Throwable, Queue.DeclareOk], Either[Throwable, Queue.DeclareOk])] =
    uuidIO.flatMap { uuid =>
      val queue = QueueDeclaration(QueueName(uuid.toString))
      (channel1.queueDeclare(queue).attempt, channel2.queueDeclare(queue).attempt).parTupled
    }

  private def validateError[E <: Throwable](
      f: Channel[F] => F[Unit],
      ch: Channel[F]
  )(implicit classTag: ClassTag[E]): F[Unit] =
    f(ch).attempt.flatMap {
      case Left(ex) =>
        ch.isOpen.map { open =>
          assert(classTag.runtimeClass.isAssignableFrom(ex.getClass), "E class is wrong")
          assert(!open, "channel is expected to get closed")
        }
      case Right(()) =>
        val ex = new AssertionError("assertion failed: expected error")
        MonadError[F, Throwable].raiseError[Unit](ex)
    }
}

object RabbitUtils {
  /* Channel & Connection shouldn't have public method `isOpen`, because they
     are created using Resource, which in turn MUST guarantee their correct state. */
  implicit class isOpenChannelOps[F[_]](val ch: ChannelExtendable[F]) extends AnyVal {
    def isOpen: F[Boolean] = ch.delay(_.isOpen)
  }

  implicit class isOpenConnectionOps[F[_]](val connection: ConnectionExtendable[F]) extends AnyVal {
    def isOpen: F[Boolean] = connection.delay(_.isOpen)
  }
}
