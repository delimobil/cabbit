package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.AMQP.Exchange
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.GetResponse
import ru.delimobil.cabbit.algebra
import ru.delimobil.cabbit.algebra.BodyEncoder
import ru.delimobil.cabbit.algebra.Channel
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.config.declaration

final class RabbitClientChannel[F[_]: ConcurrentEffect](channelOnPool: ChannelOnPool[F]) extends Channel[F] {

  private val declarator = new RabbitClientChannelDeclaration(channelOnPool)

  private val consumer = new RabbitClientChannelConsumer(channelOnPool)

  private val publisher = new RabbitClientChannelPublisher(channelOnPool)

  def basicAck(deliveryTag: algebra.DeliveryTag, multiple: Boolean): F[Unit] =
    consumer.basicAck(deliveryTag, multiple)

  def basicNack(deliveryTag: algebra.DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    consumer.basicNack(deliveryTag, multiple, requeue)

  def basicReject(deliveryTag: algebra.DeliveryTag, requeue: Boolean): F[Unit] =
    consumer.basicReject(deliveryTag, requeue)

  def basicCancel(consumerTag: algebra.ConsumerTag): F[Unit] =
    consumer.basicCancel(consumerTag)

  def queueDeclare(queueDeclaration: declaration.QueueDeclaration): F[Queue.DeclareOk] =
    declarator.queueDeclare(queueDeclaration)

  def exchangeDeclare(exchangeDeclaration: declaration.ExchangeDeclaration): F[Exchange.DeclareOk] =
    declarator.exchangeDeclare(exchangeDeclaration)

  def queueBind(queueBind: declaration.BindDeclaration): F[Unit] =
    declarator.queueBind(queueBind)

  def queueUnbind(bind: declaration.BindDeclaration): F[Queue.UnbindOk] =
    declarator.queueUnbind(bind)

  def queueDelete(queueName: algebra.QueueName): F[Queue.DeleteOk] =
    declarator.queueDelete(queueName)

  def exchangeDelete(exchangeName: algebra.ExchangeName): F[Unit] =
    declarator.exchangeDelete(exchangeName)

  def basicPublish[V](
    exchangeName: algebra.ExchangeName,
    routingKey: algebra.RoutingKey,
    properties: AMQP.BasicProperties,
    body: V,
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    publisher.basicPublish(exchangeName, routingKey, properties, body)

  def basicPublish[V](
    exchangeName: algebra.ExchangeName,
    routingKey: algebra.RoutingKey,
    properties: AMQP.BasicProperties,
    mandatory: Boolean,
    body: V,
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    publisher.basicPublish(exchangeName, routingKey, properties, mandatory, body)

  def basicQos(prefetchCount: Int): F[Unit] =
    consumer.basicQos(prefetchCount)

  def basicConsume(
    queue: algebra.QueueName,
    deliverCallback: DeliverCallback,
    cancelCallback: CancelCallback,
  ): F[algebra.ConsumerTag] =
    consumer.basicConsume(queue, deliverCallback, cancelCallback)

  def basicConsume(queue: algebra.QueueName, callback: Consumer): F[algebra.ConsumerTag] =
    consumer.basicConsume(queue, callback)

  def basicGet(queue: algebra.QueueName, autoAck: Boolean): F[GetResponse] =
    consumer.basicGet(queue, autoAck)

  def deliveryStream(queue: algebra.QueueName, prefetchCount: Int): F[(algebra.ConsumerTag, fs2.Stream[F, Delivery])] =
    consumer.deliveryStream(queue, prefetchCount)

  def isOpen: F[Boolean] =
    channelOnPool.isOpen
}
