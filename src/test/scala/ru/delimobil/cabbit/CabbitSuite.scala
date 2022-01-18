package ru.delimobil.cabbit

import cats.data.Kleisli
import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.syntax.effect._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.semigroupal._
import com.rabbitmq.client.AMQP.Queue.DeclareOk
import com.rabbitmq.client.GetResponse
import com.rabbitmq.client.ShutdownSignalException
import fs2.Stream
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import ru.delimobil.cabbit.CollectionConverters._
import ru.delimobil.cabbit.RabbitUtils._
import ru.delimobil.cabbit.api._
import ru.delimobil.cabbit.model.ContentEncoding._
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.QueueName
import ru.delimobil.cabbit.model.RoutingKey
import ru.delimobil.cabbit.model.declaration.Arguments
import ru.delimobil.cabbit.model.declaration.BindDeclaration
import ru.delimobil.cabbit.model.declaration.QueueDeclaration
import ru.delimobil.cabbit.syntax._

import java.io.IOException
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class CabbitSuite extends AnyFunSuite with BeforeAndAfterAll {

  import ru.delimobil.cabbit.encoder.string.textUtf8

  private type CheckGetFunc = Kleisli[IO, QueueName, GetResponse]

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  private val sleep = timer.sleep(100.millis)
  private val rndQueue = IO.delay(QueueName(UUID.randomUUID().toString))

  private var container: RabbitContainer = _
  private var shutdownIO: IO[Unit] = _
  private var blocker: Blocker = _
  private var connection: Connection[IO] = _
  private var channel: Channel[IO] = _
  private var rabbitUtils: RabbitUtils[IO] = _
  private var closeIO: IO[Unit] = _

  private def messagesN(n: Int) = (1 to n).map(i => s"hello from cabbit-$i").toList

  private def tupled[T1, T2](
      io1: Resource[IO, T1],
      io2: Resource[IO, T2]
  ): Resource[IO, (T1, T2)] = // for scala 2.12
    io1.flatMap(t1 => io2.map(t2 => (t1, t2)))

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    Resource
      .eval(RabbitContainer[IO])
      .flatMap { case (cont, shutdown) =>
        for {
          block <- Blocker[IO]
          conn <- cont.makeConnection[IO](block)
          ch <- conn.createChannel
        } yield (cont, shutdown, block, conn, ch)
      }
      .allocated
      .flatTap { case ((cont, shutdown, block, conn, ch), closeAction) =>
        IO.delay {
          container = cont
          shutdownIO = shutdown
          blocker = block
          connection = conn
          channel = ch
          rabbitUtils = new RabbitUtils[IO](conn, ch)
          closeIO = closeAction
        }
      }
      .unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    closeIO.unsafeRunSync()
  }

  test("connection is closed") {
    val ((_, openDuringUse), openAfterUse) =
      container
        .makeConnection[IO](blocker)
        .use(notifier => notifier.isOpen.tupleLeft(notifier))
        .mproduct(_._1.isOpen)
        .timeout(1.second)
        .toIO
        .unsafeRunSync()

    assertResult((true, false))((openDuringUse, openAfterUse))
  }

  test("channel declaration is closed") {
    val actual = checkChannelShutdown(_.createChannelDeclaration)
    assertResult((true, false))(actual)
  }

  test("channel publisher is closed") {
    val actual = checkChannelShutdown(_.createChannelPublisher)
    assertResult((true, false))(actual)
  }

  test("channel consumer is closed") {
    val actual = checkChannelShutdown(_.createChannelConsumer)
    assertResult((true, false))(actual)
  }

  test("channel is closed") {
    val actual = checkChannelShutdown(_.createChannel)
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

  test("binding on default exchange results in error") {
    rabbitUtils.spoilChannel[IOException] { ch =>
      for {
        qName <- rabbitUtils.queueDeclaredIO(Map.empty)
        _ <- ch.queueBind(BindDeclaration(qName, ExchangeName.default, RoutingKey.default))
      } yield {}
    }
  }

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
    val io: CheckGetFunc = Kleisli { qName =>
      channel.basicGet(qName, autoAck = false).flatTap { r =>
        channel.basicReject(r.deliveryTag, requeue = true)
      }
    }
    checkGet(msgCount = 1, io)
  }

  test("consumer rejects `basicGet` `requeue = false`") {
    val io: CheckGetFunc = Kleisli { qName =>
      channel.basicGet(qName, autoAck = false).flatTap { r =>
        channel.basicReject(r.deliveryTag, requeue = false)
      }
    }
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
        declareOk <- channel.queueDeclare(rabbitUtils.getQueue(bind.queueName, qProps))
        bodies <- rabbitUtils.timedRead(stream)
        _ = assert(declareOk.getConsumerCount == 1)
        _ = assert(declareOk.getMessageCount == (maxMessages - prefetched))
        _ = assert(messages == bodies)
      } yield {}
    }
  }

  ignore("consumer completes on cancel, msg gets requeued") {}

  test("Stream completes on queue removal") {
    rabbitUtils.useQueueDeclared(Map.empty) { qName =>
      for {
        stream <- channel.deliveryStream(qName, 1).map(_._2)
        _ <- channel.queueDelete(qName)
        deliveriesEither <- stream.compile.toList.timeout(300.millis)
        _ = assert(deliveriesEither == List.empty)
      } yield {}
    }
  }

  test("two exclusive queues on one connection should work") {
    container
      .makeConnection[IO](blocker)
      .flatMap(conn => tupled(conn.createChannelDeclaration, conn.createChannelDeclaration))
      .use { case (channel1, channel2) =>
        rabbitUtils
          .declareExclusive(channel1, channel2)
          .map { case (result1, result2) => assert(result1.isRight && result2.isRight) }
      }
      .unsafeRunSync()
  }

  test("only one of two exclusive queues on different connections should work") {
    val connRes =
      container.makeConnection[IO](blocker).flatMap(c => c.createChannelDeclaration.map((c, _)))

    tupled(connRes, connRes)
      .use { case ((conn1, channel1), (conn2, channel2)) =>
        rabbitUtils
          .declareExclusive(channel1, channel2)
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
    val messages = (1 to 100).map(i => s"hello from cabbit-$i").toList
    val expected = List.fill(200)(messages.take(50)).flatten
    val amount = 10000

    rabbitUtils.useQueueDeclared(Map.empty) { qName =>
      messages
        .traverse_(channel.basicPublishDirect(qName, _))
        .productR(channel.deliveryStream(qName, prefetchCount = 50))
        .map { case (_, stream) =>
          stream
            .take(amount)
            .evalTap(delivery => channel.basicReject(delivery.deliveryTag, requeue = true))
        }
        .pipe(Stream.force(_).compile.toList)
        .map { list =>
          assert(list.map(d => decodeUtf8(d.getBody)) == expected)
        }
    }
  }

  test("dead-letter reject") {
    val messages = (1 to 10).map(i => s"hello from cabbit-$i").toList

    rabbitUtils.useBinded(Map.empty) { deadLetterBind =>
      val args = Map("x-dead-letter-exchange" -> deadLetterBind.exchangeName.name)

      rabbitUtils
        .queueDeclaredIO(args)
        .flatTap(qName => messages.traverse_(msg => channel.basicPublishDirect(qName, msg)))
        .productL(sleep)
        .flatTap(qName => rabbitUtils.readReject(qName))
        .flatMap(qName =>
          rabbitUtils.readAck(deadLetterBind.queueName).product(rabbitUtils.readAck(qName))
        )
        .map { case (deadDeliveries, deliveries) =>
          assert(deliveries.isEmpty)
          assert(messages == deadDeliveries)
        }
    }
  }

  test("max-length, deliveries are dead-letter'ed from head") {
    val messages = (1 to 10).map(i => s"hello from cabbit-$i").toList

    rabbitUtils.useBinded(Map.empty) { deadLetterBind =>
      val args: Arguments =
        Map("x-dead-letter-exchange" -> deadLetterBind.exchangeName.name, "x-max-length" -> 7)
      rabbitUtils
        .queueDeclaredIO(args)
        .flatTap(qName => messages.traverse_(msg => channel.basicPublishDirect(qName, msg)))
        .productL(sleep)
        .flatMap(qName =>
          rabbitUtils.readAck(deadLetterBind.queueName).product(rabbitUtils.readAck(qName))
        )
        .map { case (deadDeliveries, deliveries) =>
          val (left, right) = messages.splitAt(3)
          assert(left == deadDeliveries)
          assert(right == deliveries)
        }
    }
  }

  test("dead-letter & remove the queue") {
    val messages = (1 to 10).map(i => s"hello from cabbit-$i").toList

    rabbitUtils.useBinded(Map.empty) { deadLetterBind =>
      val args = Map("x-dead-letter-exchange" -> deadLetterBind.exchangeName.name)
      rabbitUtils
        .bindedIO(args)
        .flatTap(bind =>
          messages.traverse_(msg => channel.basicPublishFanout(bind.exchangeName, msg))
        )
        .productL(sleep)
        .flatTap(bind => channel.queueDelete(bind.queueName))
        .productL(sleep)
        .productR(rabbitUtils.readAck(deadLetterBind.queueName))
        .map(deadDeliveries => assert(deadDeliveries.isEmpty))
    }
  }

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
        .productR(rabbitUtils.readAck(routedQueue).product(rabbitUtils.readAck(nonRoutedQueue)))
        .map { case (routed, nonRouted) =>
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
        _ <- channel.basicReject(getResponse.deliveryTag, requeue = true)
        _ <- sleep
        routed <- rabbitUtils.readAck(routedQueue)
        nonRouted <- rabbitUtils.readAck(nonRoutedQueue)
        newlyRouted <- rabbitUtils.readAck(bind.queueName)
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
      val args =
        Map("x-dead-letter-exchange" -> deadLetterBind.exchangeName.name, "x-message-ttl" -> ttl)
      for {
        bind <- rabbitUtils.bindedIO(args)
        _ <- channel.basicPublishFanout(bind.exchangeName, message)
        _ <- timer.sleep(ttl.millis)
        empty <- rabbitUtils.readAck(bind.queueName)
        dead <- rabbitUtils.readAck(deadLetterBind.queueName)
        _ = assert(empty == List.empty)
        _ = assert(dead == List(message))
      } yield {}
    }
  }

  test("Stream throws on rabbitmq shutdown") {
    intercept[ShutdownSignalException] {
      rabbitUtils.useQueueDeclared(Map.empty) { qName =>
        channel
          .deliveryStream(qName, 1)
          .map(_._2)
          .productL(shutdownIO)
          .flatMap(stream => stream.compile.toList.timeout(300.millis))
          .void
      }
    }
  }

  private def checkChannelShutdown[T <: ChannelExtendable[IO]](
      f: Connection[IO] => Resource[IO, T]
  ): (Boolean, Boolean) = {
    val ((_, openDuringUse), openAfterUse) =
      container
        .makeConnection[IO](blocker)
        .flatMap(f)
        .use(notifier => notifier.isOpen.tupleLeft(notifier))
        .mproduct(_._1.isOpen)
        .timeout(1.second)
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
        declareOk <- channel.queueDeclare(rabbitUtils.getQueue(bind.queueName, qProps))
        _ = assert(decodeUtf8(response.getBody) === message)
        _ = assert(declareOk.getConsumerCount === 0)
        _ = assert(declareOk.getMessageCount === msgCount)
      } yield {}
    }
  }
}
