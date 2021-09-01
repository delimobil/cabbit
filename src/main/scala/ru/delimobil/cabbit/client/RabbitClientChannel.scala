package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.GetResponse
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.config.declaration

final class RabbitClientChannel[F[_]: ConcurrentEffect](channelOnPool: ChannelOnPool[F]) extends Channel[F] {

  private val declarator = new RabbitClientChannelDeclaration(channelOnPool)

  private val consumer = new RabbitClientChannelConsumer(channelOnPool, RabbitClientConsumerProvider.instance[F])

  private val publisher = new RabbitClientChannelPublisher(channelOnPool)

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit] =
    consumer.basicAck(deliveryTag, multiple)

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    consumer.basicNack(deliveryTag, multiple, requeue)

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit] =
    consumer.basicReject(deliveryTag, requeue)

  def basicCancel(consumerTag: ConsumerTag): F[Unit] =
    consumer.basicCancel(consumerTag)

  def queueDeclare(queueDeclaration: declaration.QueueDeclaration): F[Queue.DeclareOk] =
    declarator.queueDeclare(queueDeclaration)

  def exchangeDeclare(exchangeDeclaration: declaration.ExchangeDeclaration): F[Unit] =
    declarator.exchangeDeclare(exchangeDeclaration)

  def queueBind(queueBind: declaration.BindDeclaration): F[Unit] =
    declarator.queueBind(queueBind)

  def queueUnbind(bind: declaration.BindDeclaration): F[Unit] =
    declarator.queueUnbind(bind)

  def queueDelete(queueName: QueueName): F[Queue.DeleteOk] =
    declarator.queueDelete(queueName)

  def exchangeDelete(exchangeName: ExchangeName): F[Unit] =
    declarator.exchangeDelete(exchangeName)

  def basicPublishDirect[V](
    queueName: QueueName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    publisher.basicPublishDirect(queueName, body, mandatory, properties)

  def basicPublishFanout[V](
    exchangeName: ExchangeName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    publisher.basicPublishFanout(exchangeName, body, mandatory, properties)

  def basicPublish[V](
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    publisher.basicPublish(exchangeName, routingKey, body, mandatory, properties)

  def basicQos(prefetchCount: Int): F[Unit] =
    consumer.basicQos(prefetchCount)

  def basicConsume(
    queue: QueueName,
    deliverCallback: DeliverCallback,
    cancelCallback: CancelCallback
  ): F[ConsumerTag] =
    consumer.basicConsume(queue, deliverCallback, cancelCallback)

  def basicConsume(queue: QueueName, callback: Consumer): F[ConsumerTag] =
    consumer.basicConsume(queue, callback)

  def basicGet(queue: QueueName, autoAck: Boolean): F[GetResponse] =
    consumer.basicGet(queue, autoAck)

  def deliveryStream(queue: QueueName, prefetchCount: Int): F[(ConsumerTag, fs2.Stream[F, Delivery])] =
    consumer.deliveryStream(queue, prefetchCount)

  def isOpen: F[Boolean] =
    channelOnPool.isOpen
}
