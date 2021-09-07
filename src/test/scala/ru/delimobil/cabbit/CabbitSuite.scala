package ru.delimobil.cabbit

import java.io.IOException
import java.util.UUID

import cats.data.Kleisli
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
import com.rabbitmq.client.AMQP.Queue.DeclareOk
import com.rabbitmq.client.GetResponse
import fs2.Stream
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import ru.delimobil.cabbit.algebra.ContentEncoding._
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.config.declaration.Arguments
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random
import scala.util.chaining._

class CabbitSuite extends AnyFunSuite with BeforeAndAfterAll {

  import ru.delimobil.cabbit.algebra.BodyEncoder.instances.textUtf8

  private type CheckGetFunc = Kleisli[IO, QueueName, GetResponse]

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  private val sleep = timer.sleep(50.millis)
  private val rndQueue = IO.delay(QueueName(UUID.randomUUID().toString))

  private var container: RabbitContainer = _
  private var connection: Connection[IO] = _
  private var channel: Channel[IO] = _
  private var rabbitUtils: RabbitUtils[IO] = _
  private var closeIO: IO[Unit] = _

  private def messagesN(n: Int) = (1 to n).map(i => s"hello from cabbit-$i").toList

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    RabbitContainer
      .make[IO]
      .mproduct(_.makeConnection[IO])
      .mproduct(_._2.createChannel)
      .allocated
      .flatTap {
        case (((cont, conn), ch), closeAction) =>
          IO.delay {
            container = cont
            connection = conn
            channel = ch
            rabbitUtils = new RabbitUtils[IO](conn, ch)
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

  test("consume on not defined queue leads to an error") {
    rabbitUtils.spoilChannel[IOException](ch => rndQueue.flatMap(ch.deliveryStream(_, 100).void))
  }

  test("queue declaration on automatically defined queue results in error") {
    rabbitUtils.spoilChannel[IOException] { ch =>
      rabbitUtils
        .queueDeclaredIO(Map.empty)
        .flatMap(qName => ch.queueDeclare(QueueDeclaration(qName)))
        .void
    }
  }

  ignore("binding on default exchange results in error") { }

  test("queue declaration returns state, publisher publishes, delivery stream subscribes") {
    val nMessages = 3
    val nConsumers = 2
    rndQueue
      .mproduct { qName =>
        val declare = channel.queueDeclare(QueueDeclaration(qName))
        val publish = messagesN(nMessages).traverse_(channel.basicPublishDirect(qName, _))
        val subscribe = List.fill(nConsumers)(channel.deliveryStream(qName, 100)).sequence_

        declare.product(publish *> sleep *> declare).product(subscribe *> sleep *> declare)
      }
      .map { case (qName, ((ok1, ok2), ok3)) =>
        def compare(ok: DeclareOk, consumers: Int, messages: Int) = {
          assert(ok.getQueue == qName.name)
          assert(ok.getConsumerCount == consumers)
          assert(ok.getMessageCount == messages)
        }

        compare(ok1, 0, 0)
        compare(ok2, 0, 3)
        compare(ok3, 2, 0)
      }
      .unsafeRunSync()
  }

  test("consumer `basicGet` `autoAck = true`") {
    val io: CheckGetFunc = Kleisli(channel.basicGet(_, autoAck = false))
    checkGet(msgCount = 0, io)
  }

  test("consumer rejects `basicGet`, `requeue = true`") {
    val io: CheckGetFunc = Kleisli(
      channel.basicGet(_, autoAck = false).flatTap { r =>
        channel.basicReject(DeliveryTag(r.getEnvelope.getDeliveryTag), requeue = true)
      }
    )
    checkGet(msgCount = 1, io)
  }

  test("consumer rejects `basicGet` `requeue = false`") {
    val io: CheckGetFunc = Kleisli(
      channel.basicGet(_, autoAck = false).flatTap { r =>
        channel.basicReject(DeliveryTag(r.getEnvelope.getDeliveryTag), requeue = false)
      }
    )
    checkGet(msgCount = 0, io)
  }

  test("consumer consumes, on start only prefetched messages are withdrawed") {
    val maxMessages = 100
    val prefetched = 51
    val messages = List.fill(maxMessages)(Random.nextString(Random.nextInt(100)))
    val qProps: Arguments = Map.empty

    rabbitUtils.useBinded(qProps) { bind =>
      for {
        _ <- messages.traverse_(channel.basicPublishFanout(bind.exchangeName, _))
        stream <- channel.deliveryStream(bind.queueName, prefetched)
        _ <- sleep
        declareOk <- channel.queueDeclare(rabbitUtils.getQueue2(bind.queueName, qProps))
        bodies <- rabbitUtils.readAck(stream)
        _ = assert(declareOk.getConsumerCount == 1)
        _ = assert(declareOk.getMessageCount == (maxMessages - prefetched))
        _ = assert(messages == bodies)
      } yield {}
    }
  }

  ignore("consumer completes on cancel, msg gets requeued") { }

  test("Stream completes on queue removal") {
    rabbitUtils.useQueueDeclared(Map.empty) { qName =>
      for {
        (_, stream) <- channel.deliveryStream(qName, 1)
        _ <- channel.queueDelete(qName)
        _ <- sleep
        deliveriesEither <- IO.race(stream.compile.toList, IO.sleep(300.millis))
        _ = assert(deliveriesEither.isLeft)
      } yield {}
    }
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
    val messages = List.fill(10)("hello from cabbit")
    val amount = 10_000
    rabbitUtils.useQueueDeclared(Map.empty) { qName =>
      channel
        .deliveryStream(qName, prefetchCount = 100)
        .productL(messages.traverse_(channel.basicPublishDirect(qName, _)))
        .map { case (_, stream) =>
          stream
            .take(amount)
            .evalTap(delivery => channel.basicReject(DeliveryTag(delivery.getEnvelope.getDeliveryTag), requeue = true))
        }
        .pipe(Stream.force(_).compile.toList)
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
          assert(left == deadDeliveries)
          assert(right == deliveries)
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
          assert(routed == List(routedMessage))
          assert(nonRouted == List(nonRoutedMessage))
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
        _ = assert(routed == List(routedMessage))
        _ = assert(nonRouted == List(nonRoutedMessage))
        _ = assert(newlyRouted == List(newlyRoutedMessage))
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
        _ = assert(dead == List(message))
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

  private def checkGet(msgCount: Int, io: CheckGetFunc): Unit = {
    val message = "hello from cabbit"

    val qProps: Arguments = Map.empty
    rabbitUtils.useBinded(qProps) { bind =>
      for {
        _ <- channel.basicPublishFanout(bind.exchangeName, message)
        response <- io.run(bind.queueName)
        _ <- sleep
        declareOk <- channel.queueDeclare(rabbitUtils.getQueue2(bind.queueName, qProps))
        _ = assert(decodeUtf8(response.getBody) === message)
        _ = assert(declareOk.getConsumerCount === 0)
        _ = assert(declareOk.getMessageCount === msgCount)
      } yield {}
    }
  }
}
