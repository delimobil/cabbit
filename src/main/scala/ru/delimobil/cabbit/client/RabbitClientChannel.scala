package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.rabbitmq.client
import com.rabbitmq.client.AMQP.BasicProperties
import fs2.Stream
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.client.consumer.RabbitClientConsumerProvider
import ru.delimobil.cabbit.config.declaration.BindDeclaration
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

import scala.jdk.CollectionConverters._

private[client] final class RabbitClientChannel[F[_]: ConcurrentEffect](
  channel: ChannelOnPool[F],
  consumerProvider: RabbitClientConsumerProvider[F]
) extends Channel[F] {

  def basicQos(prefetchCount: Int): F[Unit] =
    channel.delay(_.basicQos(prefetchCount))

  def basicConsume(
    queue: QueueName,
    deliverCallback: client.DeliverCallback,
    cancelCallback: client.CancelCallback
  ): F[ConsumerTag] =
    channel.delay(_.basicConsume(queue.name, deliverCallback, cancelCallback)).map(ConsumerTag)

  def basicConsume(queue: QueueName, consumer: client.Consumer): F[ConsumerTag] =
    channel.delay(_.basicConsume(queue.name, consumer)).map(ConsumerTag)

  def basicGet(queue: QueueName, autoAck: Boolean): F[client.GetResponse] =
    channel.delay(_.basicGet(queue.name, autoAck))

  def deliveryStream(
    queueName: QueueName,
    prefetchCount: Int
  ): F[(ConsumerTag, Stream[F, client.Delivery])] =
    for {
      _ <- basicQos(prefetchCount)
      (consumer, stream) <- consumerProvider.provide(prefetchCount)
      tag <- basicConsume(queueName, consumer)
    } yield (tag, stream)

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit] =
    channel.delay(_.basicAck(deliveryTag.number, multiple))

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    channel.delay(_.basicNack(deliveryTag.number, multiple, requeue))

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit] =
    channel.delay(_.basicReject(deliveryTag.number, requeue))

  def basicCancel(consumerTag: ConsumerTag): F[Unit] =
    channel.delay(_.basicCancel(consumerTag.name))

  def queueDeclare(queueDeclaration: QueueDeclaration): F[client.AMQP.Queue.DeclareOk] =
    channel.delay {
      _.queueDeclare(
        queueDeclaration.queueName.name,
        queueDeclaration.durable.bool,
        queueDeclaration.exclusive.bool,
        queueDeclaration.autoDelete.bool,
        queueDeclaration.arguments.asJava,
      )
    }

  def exchangeDeclare(exchangeDeclaration: ExchangeDeclaration): F[Unit] =
    channel.delay {
      _.exchangeDeclare(
        exchangeDeclaration.exchangeName.name,
        exchangeDeclaration.exchangeType,
        exchangeDeclaration.durable.bool,
        exchangeDeclaration.autoDelete.bool,
        exchangeDeclaration.internal.bool,
        exchangeDeclaration.arguments.asJava,
      )
    }.void

  def queueBind(bindDeclaration: BindDeclaration): F[Unit] =
    channel.delay {
      _.queueBind(
        bindDeclaration.queueName.name,
        bindDeclaration.exchangeName.name,
        bindDeclaration.routingKey.name,
        bindDeclaration.arguments.asJava,
      )
    }.void

  def queueUnbind(bind: BindDeclaration): F[Unit] =
    channel.delay(_.queueUnbind(bind.queueName.name, bind.exchangeName.name, bind.routingKey.name))

  def queueDelete(queueName: QueueName): F[client.AMQP.Queue.DeleteOk] =
    channel.delay(_.queueDelete(queueName.name))

  def exchangeDelete(exchangeName: ExchangeName): F[Unit] =
    channel.delay(_.exchangeDelete(exchangeName.name)).void

  def basicPublishDirect[V](
    queueName: QueueName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    basicPublish(ExchangeNameDefault, RoutingKey(queueName.name), body, mandatory, properties)

  def basicPublishFanout[V](
    exchangeName: ExchangeName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    basicPublish(exchangeName, RoutingKeyDefault, body, mandatory, properties)

  def basicPublish[V](
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] = {
    val props = encoder.alterProps(properties)
    channel.delay(_.basicPublish(exchangeName.name, routingKey.name, mandatory.bool, props, encoder.encode(body)))
  }
  def isOpen: F[Boolean] =
    channel.isOpen
}
