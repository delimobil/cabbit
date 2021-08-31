package ru.delimobil.cabbit

import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.syntax.concurrent._
import cats.effect.syntax.effect._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.semigroupal._
import fs2.Stream
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import ru.delimobil.cabbit.algebra.ContentEncoding._
import ru.delimobil.cabbit.algebra._

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
      .mproduct(cont => RabbitContainer.makeConnection[IO](cont).flatMap(RabbitUtils.make[IO]))
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

  ignore("connection is closed") {
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
          _ <- rabbitUtils.publish(List("hello from cabbit"), bind)
          _ <- sleep
          declareResult <- rabbitUtils.ch.queueDeclare(queue)
          _ = assert(declareResult.getMessageCount == 1)
        } yield {}
      }
  }

  test("consumer `basicGet` `autoAck = true`") {
    val message = "hello from cabbit"

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
    val message = "hello from cabbit"

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
    val message = "hello from cabbit"

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

  test("consumer consumes, on start only prefetched messages are withdrawed") {
    val maxMessages = 100
    val prefetched = 51
    val messages = List.fill(maxMessages)(Random.nextString(Random.nextInt(100)))

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(messages, bind)
          stream <- rabbitUtils.ch.deliveryStream(bind.queueName, prefetched)
          _ <- sleep
          declareOk <- rabbitUtils.ch.queueDeclare(queue)
          bodies <- rabbitUtils.readAck(stream)
          _ = assert(declareOk.getConsumerCount == 1)
          _ = assert(declareOk.getMessageCount == (maxMessages - prefetched))
          _ = assert(messages.length == bodies.length)
          _ = assert(messages.map(v => Right(Json.fromString(v))) == bodies)
        } yield {}
      }
  }

  ignore("consumer completes on cancel, msg gets requeued") {
    val messages = (1 to 2).map(i => s"hello from cabbit-$i").toList

    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (queue, bind) =>
        for {
          _ <- rabbitUtils.publish(messages, bind)
          (tag, stream) <- rabbitUtils.ch.deliveryStream(bind.queueName, 1)
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
            (_, stream) <- rabbitUtils.ch.deliveryStream(bind.queueName, 1)
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
    val message = "hello from cabbit"
    val amount = 10_000
    rabbitUtils.useRandomlyDeclared(Map.empty) {
      case (_, bind) =>
        rabbitUtils
          .ch
          .deliveryStream(bind.queueName, prefetchCount = 100)
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
    val messages = (1 to 10).map(i => s"hello from cabbit-$i").toList

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

  test("reject at alternate-queue DOESN'T return to newly binded to exchange queue") {
    val routingKey = RoutingKey("valid.#")
    val routedKey = RoutingKey("valid.key")
    val routedMessage = "valid-message"

    val nonRoutingKey = RoutingKey("invalid.#")
    val nonRoutedKey = RoutingKey("invalid.key")
    val nonRoutedMessage = "invalid-message"

    val newlyRoutedKey = RoutingKey("invalid.key")
    val newlyRoutedMessage = "new-message"

    rabbitUtils.useWithAE(routingKey) { case (topicEx, routedQueue, nonRoutedQueue) =>
      val exName = topicEx.exchangeName
      for {
        _ <- rabbitUtils.publishOne(exName, routedKey, routedMessage)
        _ <- rabbitUtils.publishOne(exName, nonRoutedKey, nonRoutedMessage)
        getResponse <- rabbitUtils.ch.basicGet(nonRoutedQueue.queueName, autoAck = false)
        (queue, _) <- rabbitUtils.addBind(topicEx.exchangeName, nonRoutingKey)
        _ <- sleep
        _ <- rabbitUtils.ch.basicReject(DeliveryTag(getResponse.getEnvelope.getDeliveryTag), requeue = true)
        _ <- rabbitUtils.publishOne(exName, newlyRoutedKey, newlyRoutedMessage)
        _ <- sleep
        routed <- rabbitUtils.readAll(routedQueue.queueName)
        nonRouted <- rabbitUtils.readAll(nonRoutedQueue.queueName)
        newlyRouted <- rabbitUtils.readAll(queue.queueName)
        _ = assert(routed == List(Right(Json.fromString(routedMessage))))
        _ = assert(nonRouted == List(Right(Json.fromString(nonRoutedMessage))))
        _ = assert(newlyRouted == List(Right(Json.fromString(newlyRoutedMessage))))
        _ <- rabbitUtils.ch.queueDelete(queue.queueName)
      } yield {}
    }
  }

  private def checkShutdownNotifier[F[_]: ConcurrentEffect: ContextShift: Timer, T <: ShutdownNotifier[F]](
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
