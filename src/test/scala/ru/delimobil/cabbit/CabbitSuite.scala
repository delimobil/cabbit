package ru.delimobil.cabbit

import java.util.UUID

import cats.Parallel
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Effect
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.syntax.effect._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.semigroupal._
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.GetResponse
import fs2.Stream
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import ru.delimobil.cabbit.CabbitSuite._
import ru.delimobil.cabbit.algebra.ContentEncoding._
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.config.declaration._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random
import scala.util.chaining._

class CabbitSuite extends AnyFunSuite with BeforeAndAfterAll {

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  private val sleep = timer.sleep(50.millis)

  private var container: RabbitContainer = _
  private var rabbitUtils: RabbitUtils[IO] = _
  private var closeIO: IO[Unit] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    RabbitContainer
      .make[IO]
      .mproduct(cont => RabbitContainer.makeConnection[IO](cont).flatMap(make[IO]))
      .allocated
      .flatTap {
        case ((cont, utils), closeAction) =>
          IO.delay {
            container = cont
            rabbitUtils = utils
            closeIO = closeAction
          }
      }
      .unsafeRunSync
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    closeIO.unsafeRunSync()
  }

  test("connection is closed") {
    val actual = checkShutdownNotifier[IO, Connection[IO]](container)(Resource.pure[IO, Connection[IO]](_))
    assertResult((true, false))(actual)
  }

  ignore("channel declaration is closed") {
    val actual = checkShutdownNotifier[IO, ChannelDeclaration[IO]](container)(_.createChannelDeclaration)
    assertResult((true, false))(actual)
  }

  ignore("channel publisher is closed") {
    val actual = checkShutdownNotifier[IO, ChannelPublisher[IO]](container)(_.createChannelPublisher)
    assertResult((true, false))(actual)
  }

  ignore("channel consumer is closed") {
    val actual = checkShutdownNotifier[IO, ChannelConsumer[IO]](container)(_.createChannelConsumer)
    assertResult((true, false))(actual)
  }

  test("queue declaration returns state") {
    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, _) =>
        for {
          declareResult <- rabbitUtils.ch.queueDeclare(queue)
          _ = assert(declareResult.getQueue == queue.queueName.name)
          _ = assert(declareResult.getConsumerCount == 0)
          _ = assert(declareResult.getMessageCount == 0)
        } yield {}
      }
  }

  test("publisher publishes") {
    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(List("hello from fs2-rabbit"), bind)
          _ <- sleep
          declareResult <- rabbitUtils.ch.queueDeclare(queue)
          _ = assert(declareResult.getMessageCount == 1)
        } yield {}
      }
  }

  test("consumer `basicGet` `autoAck = true`") {
    val message = "hello from fs2-rabbit"

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(List(message), bind)
          _ <- sleep
          response <- rabbitUtils.ch.basicGet(bind.queueName, autoAck = true)
          declareOk <- rabbitUtils.ch.queueDeclare(queue)
          bodyResponse = ungzip(response.getBody).map(decodeUtf8).flatMap(parse)
          _ = assert(bodyResponse === Right(Json.fromString(message)))
          _ = assert(declareOk.getConsumerCount === 0)
          _ = assert(declareOk.getMessageCount === 0)
        } yield {}
      }
  }

  test("consumer rejects `basicGet`, `requeue = true`") {
    val message = "hello from fs2-rabbit"

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(List(message), bind)
          _ <- sleep
          response <- rabbitUtils.ch.basicGet(bind.queueName, autoAck = false)
          _ <- rabbitUtils.ch.basicReject(DeliveryTag(response.getEnvelope.getDeliveryTag), requeue = true)
          _ <- sleep
          declareOk <- rabbitUtils.ch.queueDeclare(queue)
          bodyResponse = ungzip(response.getBody).map(decodeUtf8).flatMap(parse)
          _ = assert(bodyResponse === Right(Json.fromString(message)))
          _ = assert(declareOk.getConsumerCount === 0)
          _ = assert(declareOk.getMessageCount === 1)
        } yield {}
      }
  }

  test("consumer rejects `basicGet` `requeue = false`") {
    val message = "hello from fs2-rabbit"

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(List(message), bind)
          _ <- sleep
          response <- rabbitUtils.ch.basicGet(bind.queueName, autoAck = false)
          _ <- rabbitUtils.ch.basicReject(DeliveryTag(response.getEnvelope.getDeliveryTag), requeue = false)
          _ <- sleep
          declareOk <- rabbitUtils.ch.queueDeclare(queue)
          bodyResponse = ungzip(response.getBody).map(decodeUtf8).flatMap(parse)
          _ = assert(bodyResponse === Right(Json.fromString(message)))
          _ = assert(declareOk.getConsumerCount === 0)
          _ = assert(declareOk.getMessageCount === 0)
        } yield {}
      }
  }

  test("consumer consumes") {
    val maxMessages = 1000
    val prefetched = 51
    val messages = List.fill(maxMessages)(Random.nextString(Random.nextInt(100)))

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(messages, bind)
          (_, stream) <- rabbitUtils.deliveryStream(bind.queueName, prefetched)
          _ <- sleep
          declareOk <- rabbitUtils.ch.queueDeclare(queue)
          deliveries <- rabbitUtils.readAckN(stream, maxMessages)
          bodies = deliveries.map(msg => ungzip(msg.getBody).map(decodeUtf8).flatMap(parse))
          _ = assert(declareOk.getConsumerCount == 1)
          _ = assert(declareOk.getMessageCount == (maxMessages - prefetched))
          _ = assert(messages.map(v => Right(Json.fromString(v))) === bodies)
        } yield {}
      }
  }

  ignore("consumer completes on cancel, msg gets requeued") {
    val messages = (1 to 2).map(i => s"hello from fs2-rabbit-$i").toList

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(messages, bind)
          (tag, stream) <- rabbitUtils.deliveryStream(bind.queueName, 1)
          _ <- rabbitUtils.ch.basicCancel(tag)
          _ <- sleep
          deliveriesEither <- IO.race(stream.compile.toList, IO.sleep(300.millis))
          declareOk2 <- rabbitUtils.ch.queueDeclare(queue)
          _ = assert(deliveriesEither.isLeft)
          _ = assert(declareOk2.getMessageCount == 2)
        } yield {}
      }
  }

  test("Stream completes on queue removal") {
    rabbitUtils.declareAcquire(Map.empty)
      .flatMap {
        case (_, bind) =>
          for {
            (_, stream) <- rabbitUtils.deliveryStream(bind.queueName, 1)
            _ <- rabbitUtils.declareRelease(bind)
            _ <- sleep
            deliveriesEither <- IO.race(stream.compile.toList, IO.sleep(300.millis))
            _ = assert(deliveriesEither.isLeft)
          } yield {}
      }
      .unsafeRunSync()
  }

  test("two exclusive queues on one connection should work") {
    RabbitContainer
      .makeConnection[IO](container)
      .flatMap(conn => (conn.createChannelDeclaration, conn.createChannelDeclaration).tupled)
      .use { case (channel1, channel2) =>
        rabbitUtils.declareExclusive(channel1, channel2)
          .map { case (result1, result2) => assert(result1.isRight && result2.isRight) }
      }
      .unsafeRunSync()
  }

  test("only one of two exclusive queues on different connections should work") {
    val connRes = RabbitContainer.makeConnection[IO](container).mproduct(_.createChannelDeclaration)

    (connRes, connRes)
      .tupled
      .use { case ((conn1, channel1), (conn2, channel2)) =>
        rabbitUtils.declareExclusive(channel1, channel2)
          .product((channel1.isOpen, channel2.isOpen, conn1.isOpen, conn2.isOpen).tupled)
          .map { case ((result1, result2), (ch1Open, ch2Open, conn1Open, conn2Open)) =>
            assert(result1.isRight ^ result2.isRight)
            assert(ch1Open ^ ch2Open)
            assert(conn1Open && conn2Open)
          }
      }
      .unsafeRunSync()
  }

  test("ten thousand requeues") {
    val message = "hello from fs2-rabbit"
    val amount = 10_000
    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (_, bind) =>
        rabbitUtils
          .deliveryStream(bind.queueName, prefetch = 100)
          .productL(rabbitUtils.publish(List.fill(10)(message), bind))
          .map { case (_, stream) =>
            stream
              .take(amount)
              .evalTap { delivery =>
                val tag = DeliveryTag(delivery.getEnvelope.getDeliveryTag)
                rabbitUtils.ch.basicReject(tag, requeue = true)
              }
          }
          .pipe(Stream.force)
          .compile
          .toList
          .map(list => assert(list.length == amount))
    }
  }

  test("dead-letter & max-length") {
    val messages = (1 to 10).map(i => s"hello from fs2-rabbit-$i").toList

    rabbitUtils
      .useWithDeadQueue(Some(7)) { case (dead, queue, bind) =>
        rabbitUtils.publish(messages, bind)
          .productR(sleep)
          .productR(rabbitUtils.readAll(dead.queueName).product(rabbitUtils.readAll(queue.queueName)))
          .map { case (deadDeliveries, deliveries) =>
            val (left, right) = messages.splitAt(3)
            assert(left.map(s => Right(Json.fromString(s))) == deadDeliveries)
            assert(right.map(s => Right(Json.fromString(s))) == deliveries)
          }
      }
  }

  ignore("dead-letter & remove the queue") { }

  test("alternate-exchange") {
    val routingKey = RoutingKey("valid.#")
    val routedKey = RoutingKey("valid.key")
    val routedMessage = "valid-message"
    val nonRoutedKey = RoutingKey("invalid.key")
    val nonRoutedMessage = "invalid-message"
    rabbitUtils.useWithAE(routingKey) { case (topicEx, routedQueue, nonRoutedQueue) =>
      val exName = topicEx.exchangeName
      rabbitUtils
        .publishOne(exName, routedKey, routedMessage)
        .productR(rabbitUtils.publishOne(exName, nonRoutedKey, nonRoutedMessage))
        .productR(sleep)
        .productR(rabbitUtils.readAll(routedQueue.queueName).product(rabbitUtils.readAll(nonRoutedQueue.queueName)))
        .map { case(routed, nonRouted) =>
          assert(routed == List(Right(Json.fromString(routedMessage))))
          assert(nonRouted == List(Right(Json.fromString(nonRoutedMessage))))
        }
    }
  }
}

private object CabbitSuite {

  final class RabbitUtils[F[_]: ConcurrentEffect: Parallel: Timer](val ch: Channel[F]) {

    import ru.delimobil.cabbit.algebra.BodyEncoder.instances.jsonGzip

    def deliveryStream(queue: QueueName, prefetch: Int): F[(ConsumerTag, Stream[F, Delivery])] =
      ch.deliveryStream(queue, prefetch)

    def readAckN(stream: Stream[F, Delivery], n: Int): F[List[Delivery]] =
      stream
        .evalTap(d => ch.basicAck(DeliveryTag(d.getEnvelope.getDeliveryTag), multiple = false))
        .take(n)
        .compile
        .toList

    def readAll(queue: QueueName, timeout: FiniteDuration = 100.millis): F[List[Either[Exception, Json]]] =
      ch.deliveryStream(queue, 100)
        .flatMap { case (tag, stream) =>
          stream
            .evalTap(d => ch.basicAck(DeliveryTag(d.getEnvelope.getDeliveryTag), multiple = false))
            .concurrently(Stream.eval_(Timer[F].sleep(timeout) *> ch.basicCancel(tag)))
            .compile
            .toList
        }
        .map(_.map(d => ungzip(d.getBody).map(decodeUtf8).flatMap(parse)))

    def publish(messages: List[String], bind: BindDeclaration): F[Unit] =
      messages.traverse_(publishOne(bind.exchangeName, bind.routingKey, _))

    def publishOne(exchange: ExchangeName, key: RoutingKey, msg: String) =
      ch.basicPublish(exchange, key, new AMQP.BasicProperties, mandatory = true, msg)

    def declareAcquire(qProps: Arguments): F[(QueueDeclaration, BindDeclaration)] =
      uuidIO.flatMap { uuid =>
        val (exchange, queue, bind) = getDirectNonExclusive(uuid, qProps)
        ch.exchangeDeclare(exchange) *> ch.queueDeclare(queue) *> ch.queueBind(bind).as((queue, bind))
      }

    def declareRelease(bind: BindDeclaration): F[Unit] =
      ch.queueUnbind(bind) <* ch.exchangeDelete(bind.exchangeName) <* ch.queueDelete(bind.queueName)

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

    private def uuidIO: F[UUID] =
      Sync[F].delay(UUID.randomUUID())

    def useWithAE(
      rk: RoutingKey
    )(testFunc: (ExchangeDeclaration, QueueDeclaration, QueueDeclaration) => F[Unit]): Unit =
      (uuidIO, uuidIO)
        .tupled
        .flatMap { case (uuid1, uuid2) =>
          val aeExchange = fanout(uuid1)
          val aeQueue = nonExclusive(uuid1, Map.empty)
          val aeBind = BindDeclaration(aeQueue.queueName, aeExchange.exchangeName, RoutingKey(""))
          val aeDec = ch.exchangeDeclare(aeExchange) *> ch.queueDeclare(aeQueue) *> ch.queueBind(aeBind)

          val topicExchange = topic(uuid2, Map("alternate-exchange" -> aeExchange.exchangeName.name))
          val topicQueue = nonExclusive(uuid2, Map.empty)
          val topicBind = BindDeclaration(topicQueue.queueName, topicExchange.exchangeName, rk)
          val topicDec = ch.exchangeDeclare(topicExchange) *> ch.queueDeclare(topicQueue) *> ch.queueBind(topicBind)

          aeDec *> topicDec.as((topicExchange, topicQueue, aeQueue, aeBind, topicBind))
        }
        .bracket { case (ex, q1, q2, _, _) =>
          testFunc(ex, q1, q2)
        } (res => declareRelease(res._4) *> declareRelease(res._5))
        .toIO
        .unsafeRunSync()

    def declareExclusive(
      channel1: ChannelDeclaration[F],
      channel2: ChannelDeclaration[F]
    ): F[(Either[Throwable, Queue.DeclareOk], Either[Throwable, Queue.DeclareOk])] =
      uuidIO
        .flatMap { uuid =>
          val queue = exclusive(uuid)
          (channel1.queueDeclare(queue).attempt, channel2.queueDeclare(queue).attempt).parTupled
        }

    private def getExchange(uuid: UUID, tpe: BuiltinExchangeType, props: Arguments) =
      ExchangeDeclaration(
        ExchangeName(s"the-exchange-$uuid"),
        tpe,
        DurableConfig.NonDurable,
        AutoDeleteConfig.NonAutoDelete,
        InternalConfig.NonInternal,
        props,
      )

    private def direct(uuid: UUID): ExchangeDeclaration =
      getExchange(uuid, BuiltinExchangeType.DIRECT, Map.empty)

    private def topic(uuid: UUID, props: Arguments): ExchangeDeclaration =
      getExchange(uuid, BuiltinExchangeType.TOPIC, props)

    private def fanout(uuid: UUID): ExchangeDeclaration =
      getExchange(uuid, BuiltinExchangeType.FANOUT, Map.empty)

    private def getQueue(uuid: UUID, exclusivity: ExclusiveConfig, props: Arguments): QueueDeclaration =
      QueueDeclaration(
        QueueName(s"the-queue-$uuid"),
        DurableConfig.NonDurable,
        exclusivity,
        AutoDeleteConfig.NonAutoDelete,
        props,
      )

    private def nonExclusive(uuid: UUID, props: Arguments): QueueDeclaration =
      getQueue(uuid, ExclusiveConfig.NonExclusive, props)

    private def exclusive(uuid: UUID): QueueDeclaration =
      getQueue(uuid, ExclusiveConfig.Exclusive, Map.empty)

    def getDirectNonExclusive(
      uuid: UUID,
      qProps: Arguments,
    ): (ExchangeDeclaration, QueueDeclaration, BindDeclaration) = {
      val exchange = direct(uuid)
      val queue = nonExclusive(uuid, qProps)
      val binding = BindDeclaration(queue.queueName, exchange.exchangeName, RoutingKey("the-key"))

      (exchange, queue, binding)
    }
  }

  def make[F[_]: ConcurrentEffect: Parallel: Timer](conn: Connection[F]): Resource[F, RabbitUtils[F]] =
    conn.createChannel.map(ch => new RabbitUtils(ch))

  def checkShutdownNotifier[F[_]: ConcurrentEffect: ContextShift: Timer, T <: ShutdownNotifier[F]](
    container: RabbitContainer
  )(
    f: Connection[F] => Resource[F, T]
  ): (Boolean, Boolean) = {
    val ((_, openDuringUse), openAfterUse) =
      RabbitContainer
        .makeConnection[F](container)
        .flatMap(f)
        .use(notifier => notifier.isOpen.tupleLeft(notifier))
        .mproduct(_._1.isOpen)
        .timeout(100.millis)
        .toIO
        .unsafeRunSync()

    (openDuringUse, openAfterUse)
  }
}
