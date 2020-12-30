package ru.delimobil.cabbit

import java.util.UUID

import cats.Id
import cats.arrow.FunctionK
import cats.data.NonEmptyList
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Timer
import cats.instances.list._
import cats.syntax.traverse._
import cats.~>
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.BuiltinExchangeType
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import ru.delimobil.cabbit.algebra.BodyEncoder.instances.jsonGzip
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.algebra.Connection
import ru.delimobil.cabbit.algebra.ContentEncoding._
import ru.delimobil.cabbit.config.Fs2RabbitConfig
import ru.delimobil.cabbit.config.Fs2RabbitConfig.Fs2RabbitNodeConfig
import ru.delimobil.cabbit.config.declaration.AutoDeleteConfig
import ru.delimobil.cabbit.config.declaration.BindDeclaration
import ru.delimobil.cabbit.config.declaration.DurableConfig
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.ExclusiveConfig
import ru.delimobil.cabbit.config.declaration.InternalConfig
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Fs2RabbitSuite extends AnyFunSuite with BeforeAndAfterAll {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val connectionResource: Resource[IO, Connection[IO]] =
    for {
      container <- RabbitContainerProvider.resource[IO]
      config =
        Fs2RabbitConfig(
          NonEmptyList.one(Fs2RabbitNodeConfig(container.host, container.port)),
          virtualHost = "/",
          connectionTimeout = 10,
          username = None,
          password = None
        )
      connectionFactory = ConnectionFactoryProvider.provide[IO](config)
      newConnection <- connectionFactory.newConnection
    } yield newConnection

  val (connection, closeIO) = connectionResource.allocated.unsafeRunSync()

  def getDeclarations(uuid: UUID): (ExchangeDeclaration, QueueDeclaration, BindDeclaration) = {
    val exchange =
      ExchangeDeclaration(
        s"the-exchange-$uuid",
        BuiltinExchangeType.DIRECT,
        DurableConfig.NonDurable,
        AutoDeleteConfig.AutoDelete,
        InternalConfig.NonInternal,
        Map.empty
      )

    val queue =
      QueueDeclaration(
        s"the-queue-$uuid",
        DurableConfig.NonDurable,
        ExclusiveConfig.NonExclusive,
        AutoDeleteConfig.AutoDelete,
        Map.empty
      )

    val binding = BindDeclaration(queue.queueName, exchange.exchangeName, "the-key")

    (exchange, queue, binding)
  }

  def declare(channel: ChannelOnPool[IO]): IO[(ExchangeDeclaration, QueueDeclaration, BindDeclaration)] =
    for {
      declarations <- IO.delay(getDeclarations(UUID.randomUUID()))
      (exchange, queue, binding) = declarations
      declaration = connection.channelDeclaration(channel)
      _ <- declaration.exchangeDeclare(exchange)
      _ <- declaration.queueDeclare(queue)
      _ <- declaration.queueBind(binding)
    } yield (exchange, queue, binding)

  test("queue declaration returns state") {
    connection.createChannelOnPool.use { channel =>
      for {
        declarations <- declare(channel)
        (_, queue, _) = declarations
        declarationChannel = connection.channelDeclaration(channel)
        declareResult <- declarationChannel.queueDeclare(queue)
        _ = assert(declareResult.getQueue == queue.queueName)
        _ = assert(declareResult.getConsumerCount == 0)
        _ = assert(declareResult.getMessageCount == 0)
      } yield {}
    }
  }

  private def channelWithPublishedMessage(
    messages: List[String]
  )(f: (ChannelOnPool[IO], QueueDeclaration) => IO[Unit]): Unit = {
    val twoChannels =
      for {
        consumerChannel <- connection.createChannelOnPool
        publisherChannel <- connection.createChannelOnPool
      } yield (consumerChannel, publisherChannel)

    val action =
      twoChannels.use { case(consumerChannel, publisherChannel) =>
        for {
          declarations <- declare(publisherChannel)
          (_, queue, bind) = declarations
          publisher = connection.channelPublisher(publisherChannel)
          _ <-
            messages.traverse { message =>
              publisher.basicPublish(
                bind.exchangeName,
                bind.routingKey,
                new BasicProperties,
                mandatory = true,
                message
              )
            }
          _ <- IO.sleep(50.millis)
          _ <- f(consumerChannel, queue)
        } yield {}
      }

    action.unsafeRunSync()
  }

  test("publisher publishes") {
    channelWithPublishedMessage(List("hello from fs2-rabbit")) { case (channel, queue) =>
      val consumer = connection.channelDeclaration(channel)
      consumer.queueDeclare(queue).map(declareResult => assert(declareResult.getMessageCount == 1))
    }
  }

  test("consumer gets") {
    val message = "hello from fs2-rabbit"
    channelWithPublishedMessage(List(message)) { case (channel, queue) =>
      val declaration = connection.channelDeclaration(channel)
      val consumer = connection.channelConsumer(channel)
      for {
        response <- consumer.basicGet(queue.queueName, autoAck = true)
        declareOk <- declaration.queueDeclare(queue)
        bodyResponse = parse(decodeUtf8(ungzip(response.getBody).getOrElse(???))).getOrElse(???)
        _ = assert(bodyResponse == Json.fromString(message))
        _ = assert(declareOk.getConsumerCount == 0)
        _ = assert(declareOk.getMessageCount == 0)
      } yield {}
    }
  }

  test("consumer rejects get with requeue") {
    val message = "hello from fs2-rabbit"

    channelWithPublishedMessage(List(message)) { case (channel, queue) =>
      val declaration = connection.channelDeclaration(channel)
      val consumer = connection.channelConsumer(channel)
      for {
        response <- consumer.basicGet(queue.queueName, autoAck = false)
        _ <- consumer.basicReject(response.getEnvelope.getDeliveryTag, requeue = true)
        declareOk <- declaration.queueDeclare(queue)
        bodyResponse = parse(decodeUtf8(ungzip(response.getBody).getOrElse(???))).getOrElse(???)
        _ = assert(bodyResponse == Json.fromString(message))
        _ = assert(declareOk.getConsumerCount == 0)
        _ = assert(declareOk.getMessageCount == 1)
      } yield {}
    }
  }

  test("consumer rejects get without requeue") {
    val message = "hello from fs2-rabbit"

    channelWithPublishedMessage(List(message)) { case (channel, queue) =>
      val declaration = connection.channelDeclaration(channel)
      val consumer = connection.channelConsumer(channel)
      for {
        response <- consumer.basicGet(queue.queueName, autoAck = false)
        _ <- consumer.basicReject(response.getEnvelope.getDeliveryTag, requeue = false)
        declareOk <- declaration.queueDeclare(queue)
        bodyResponse = parse(decodeUtf8(ungzip(response.getBody).getOrElse(???))).getOrElse(???)
        _ = assert(bodyResponse == Json.fromString(message))
        _ = assert(declareOk.getConsumerCount == 0)
        _ = assert(declareOk.getMessageCount == 0)
      } yield {}
    }
  }

  test("consumer consumes") {
    val messages = (1 to 100).map(i => s"hello from fs2-rabbit-$i").toList

    channelWithPublishedMessage(messages) { case(channel, queue) =>
      val declaration = connection.channelDeclaration(channel)
      val consumer = connection.channelConsumer(channel)
      for {
        deliveryStream <- consumer.deliveryStream(queue.queueName, prefetchCount = 51)
        (_, stream) = deliveryStream
        declareOk <- declaration.queueDeclare(queue)
        _ = assert(declareOk.getConsumerCount == 1)
        _ = assert(declareOk.getMessageCount == 49)
        streamedMessages <-
          stream
            .evalTap(delivery => consumer.basicAck(delivery.getEnvelope.getDeliveryTag, multiple = false))
            .take(100)
            .compile
            .toList
        streamedBodies = streamedMessages.map { message =>
          parse(decodeUtf8(ungzip(message.getBody).getOrElse(???))).getOrElse(???)
        }
        _ = assert(messages.map(Json.fromString) == streamedBodies)
      } yield {}
    }
  }

  test("consumer requeue on consume") {
    val messages = (1 to 2).map(i => s"hello from fs2-rabbit-$i").toList

    channelWithPublishedMessage(messages) { case(channel, queue) =>
      val declaration = connection.channelDeclaration(channel)
      val consumer = connection.channelConsumer(channel)
      for {
        deliveryStream <- consumer.deliveryStream(queue.queueName, prefetchCount = 1)
        (consumerTag, stream) = deliveryStream
        declareOk <- declaration.queueDeclare(queue)
        _ = assert(declareOk.getMessageCount == 1)
        deliveries <- stream.take(1).compile.toList
        delivery = deliveries.head
        _ <- consumer.basicReject(delivery.getEnvelope.getDeliveryTag, requeue = true)
        declareOk2 <- declaration.queueDeclare(queue)
        _ = assert(declareOk2.getMessageCount == 1)
      } yield {}
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    closeIO.unsafeRunSync()
  }
}