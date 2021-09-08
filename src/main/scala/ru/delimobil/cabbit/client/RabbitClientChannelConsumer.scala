package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.rabbitmq.client
import fs2.Stream
import ru.delimobil.cabbit.algebra.ChannelConsumer
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.algebra.ConsumerTag
import ru.delimobil.cabbit.algebra.DeliveryTag
import ru.delimobil.cabbit.algebra.QueueName
import ru.delimobil.cabbit.client.consumer.RabbitClientConsumerProvider

final class RabbitClientChannelConsumer[F[_]: ConcurrentEffect](
  channelOnPool: ChannelOnPool[F],
  consumerProvider: RabbitClientConsumerProvider[F],
) extends ChannelConsumer[F] {

  def basicQos(prefetchCount: Int): F[Unit] =
    channelOnPool.delay(_.basicQos(prefetchCount))

  def basicConsume(
    queue: QueueName,
    deliverCallback: client.DeliverCallback,
    cancelCallback: client.CancelCallback,
  ): F[ConsumerTag] =
    channelOnPool.delay(_.basicConsume(queue.name, deliverCallback, cancelCallback)).map(ConsumerTag(_))

  def basicConsume(queue: QueueName, consumer: client.Consumer): F[ConsumerTag] =
    channelOnPool.delay(_.basicConsume(queue.name, consumer)).map(ConsumerTag(_))

  def basicGet(queue: QueueName, autoAck: Boolean): F[client.GetResponse] =
    channelOnPool.delay(_.basicGet(queue.name, autoAck))

  def deliveryStream(
    queueName: QueueName,
    prefetchCount: Int,
  ): F[(ConsumerTag, Stream[F, client.Delivery])] =
    for {
      _ <- basicQos(prefetchCount)
      res <- consumerProvider.provide(prefetchCount)
      (consumer, stream) = res
      tag <- basicConsume(queueName, consumer)
    } yield (tag, stream)

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit] =
    channelOnPool.delay(_.basicAck(deliveryTag.number, multiple))

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    channelOnPool.delay(_.basicNack(deliveryTag.number, multiple, requeue))

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit] =
    channelOnPool.delay(_.basicReject(deliveryTag.number, requeue))

  def basicCancel(consumerTag: ConsumerTag): F[Unit] =
    channelOnPool.delay(_.basicCancel(consumerTag.name))

  def isOpen: F[Boolean] =
    channelOnPool.isOpen
}
