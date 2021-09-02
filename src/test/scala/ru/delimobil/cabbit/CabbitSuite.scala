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
import cats.syntax.foldable._
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

  import ru.delimobil.cabbit.algebra.BodyEncoder.instances.jsonGzip // change to textUtf8

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  private val sleep = timer.sleep(50.millis)

  private var container: RabbitContainer = _
  private var channel: Channel[IO] = _
  private var rabbitUtils: RabbitUtils[IO] = _
  private var closeIO: IO[Unit] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    RabbitContainer
      .make[IO]
      .mproduct(_.makeConnection[IO].flatMap(conn => conn.createChannel))
      .allocated
      .flatTap {
        case ((cont, ch), closeAction) =>
          IO.delay {
            container = cont
            channel = ch
            rabbitUtils = new RabbitUtils[IO](ch)
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
          declareResult <- channel.queueDeclare(queue)
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
          declareResult <- channel.queueDeclare(queue)
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
          response <- channel.basicGet(bind.queueName, autoAck = true)
          declareOk <- channel.queueDeclare(queue)
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
          response <- channel.basicGet(bind.queueName, autoAck = false)
          _ <- channel.basicReject(DeliveryTag(response.getEnvelope.getDeliveryTag), requeue = true)
          _ <- sleep
          declareOk <- channel.queueDeclare(queue)
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
          response <- channel.basicGet(bind.queueName, autoAck = false)
          _ <- channel.basicReject(DeliveryTag(response.getEnvelope.getDeliveryTag), requeue = false)
          _ <- sleep
          declareOk <- channel.queueDeclare(queue)
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
          stream <- channel.deliveryStream(bind.queueName, prefetched)
          _ <- sleep
          declareOk <- channel.queueDeclare(queue)
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
          (tag, stream) <- channel.deliveryStream(bind.queueName, 1)
          _ <- channel.basicCancel(tag)
          _ <- sleep
          deliveriesEither <- IO.race(stream.compile.toList, IO.sleep(300.millis))
          declareOk2 <- channel.queueDeclare(queue)
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
            (_, stream) <- channel.deliveryStream(bind.queueName, 1)
            _ <- rabbitUtils.declareRelease(bind)
            _ <- sleep
            deliveriesEither <- IO.race(stream.compile.toList, IO.sleep(300.millis))
            _ = assert(deliveriesEither.isLeft)
          } yield {}
      }
      .unsafeRunSync()
  }

  test("two exclusive queues on one connection should work") {
    container
      .makeConnection[IO]
      .flatMap(conn => (conn.createChannelDeclaration, conn.createChannelDeclaration).tupled)
      .use { case (channel1, channel2) =>
        rabbitUtils.declareExclusive(channel1, channel2)
          .map { case (result1, result2) => assert(result1.isRight && result2.isRight) }
      }
      .unsafeRunSync()
  }

  test("only one of two exclusive queues on different connections should work") {
    val connRes = container.makeConnection[IO].mproduct(_.createChannelDeclaration)

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
        channel
          .deliveryStream(bind.queueName, prefetchCount = 100)
          .productL(rabbitUtils.publish(List.fill(10)(message), bind))
          .map { case (_, stream) =>
            stream
              .take(amount)
              .evalTap { delivery =>
                val tag = DeliveryTag(delivery.getEnvelope.getDeliveryTag)
                channel.basicReject(tag, requeue = true)
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

    rabbitUtils.useBinded(Map.empty) { deadLetterBind =>
      val args = Map("x-dead-letter-exchange" -> deadLetterBind.exchangeName.name, "x-max-length" -> 7)
      rabbitUtils
        .bindedIO(args)
        .flatTap(bind => messages.traverse_(msg => channel.basicPublishFanout(bind.exchangeName, msg)))
        .productL(sleep)
        .flatMap(bind => rabbitUtils.readAll(deadLetterBind.queueName).product(rabbitUtils.readAll(bind.queueName)))
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
    rabbitUtils.useAlternateExchange(routingKey) { case (exName, routedQueue, nonRoutedQueue) =>
      channel
        .basicPublish(exName, routedKey, routedMessage)
        .productR(channel.basicPublish(exName, nonRoutedKey, nonRoutedMessage))
        .productR(sleep)
        .productR(rabbitUtils.readAll(routedQueue).product(rabbitUtils.readAll(nonRoutedQueue)))
        .map { case(routed, nonRouted) =>
          assert(routed == List(Right(Json.fromString(routedMessage))))
          assert(nonRouted == List(Right(Json.fromString(nonRoutedMessage))))
        }
    }
  }

  test("reject at alternate-queue DOESN'T return to newly binded queue") {
    val routingKey = RoutingKey("valid.#")
    val routedKey = RoutingKey("valid.key")
    val routedMessage = "valid-message"

    val nonRoutingKey = RoutingKey("invalid.#")
    val nonRoutedKey = RoutingKey("invalid.key")
    val nonRoutedMessage = "invalid-message"

    val newlyRoutedKey = RoutingKey("invalid.key")
    val newlyRoutedMessage = "new-message"

    rabbitUtils.useAlternateExchange(routingKey) { case (exName, routedQueue, nonRoutedQueue) =>
      for {
        _ <- channel.basicPublish(exName, routedKey, routedMessage)
        _ <- channel.basicPublish(exName, nonRoutedKey, nonRoutedMessage)
        getResponse <- channel.basicGet(nonRoutedQueue, autoAck = false)
        bind <- rabbitUtils.bindQueueToExchangeIO(exName, nonRoutingKey, Map.empty)
        _ <- sleep
        _ <- channel.basicPublish(exName, newlyRoutedKey, newlyRoutedMessage)
        _ <- channel.basicReject(DeliveryTag(getResponse.getEnvelope.getDeliveryTag), requeue = true)
        _ <- sleep
        routed <- rabbitUtils.readAll(routedQueue)
        nonRouted <- rabbitUtils.readAll(nonRoutedQueue)
        newlyRouted <- rabbitUtils.readAll(bind.queueName)
        _ = assert(routed == List(Right(Json.fromString(routedMessage))))
        _ = assert(nonRouted == List(Right(Json.fromString(nonRoutedMessage))))
        _ = assert(newlyRouted == List(Right(Json.fromString(newlyRoutedMessage))))
      } yield {}
    }
  }

  test("ttl") {
    val message = "ttl-message"
    val ttl = 150

    rabbitUtils.useBinded(Map.empty) { deadLetterBind =>
      val args = Map("x-dead-letter-exchange" -> deadLetterBind.exchangeName.name, "x-message-ttl" -> ttl)
      for {
        bind <- rabbitUtils.bindedIO(args)
        _ <- channel.basicPublishFanout(bind.exchangeName, message)
        _ <- timer.sleep(ttl.millis)
        (empty, dead) <- rabbitUtils.readAll(bind.queueName).product(rabbitUtils.readAll(deadLetterBind.queueName))
        _ = assert(empty == List.empty)
        _ = assert(dead == List(Right(Json.fromString(message))))
      } yield {}
    }
  }

  private def checkShutdownNotifier[F[_]: ConcurrentEffect: ContextShift: Timer, T <: ShutdownNotifier[F]](
    container: RabbitContainer
  )(
    f: Connection[F] => Resource[F, T]
  ): (Boolean, Boolean) = {
    val ((_, openDuringUse), openAfterUse) =
      container
        .makeConnection[F]
        .flatMap(f)
        .use(notifier => notifier.isOpen.tupleLeft(notifier))
        .mproduct(_._1.isOpen)
        .timeout(100.millis)
        .toIO
        .unsafeRunSync()

    (openDuringUse, openAfterUse)
  }
}
