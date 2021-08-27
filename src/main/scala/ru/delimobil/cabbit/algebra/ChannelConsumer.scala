package ru.delimobil.cabbit.algebra

import com.rabbitmq.client
import fs2.Stream

trait ChannelConsumer[F[_]] extends ChannelAcker[F] {

  def basicQos(prefetchCount: Int): F[Unit]

  def basicConsume(
    queue: QueueName,
    deliverCallback: client.DeliverCallback,
    cancelCallback: client.CancelCallback,
  ): F[ConsumerTag]

  def basicConsume(queue: QueueName, callback: client.Consumer): F[ConsumerTag]

  def basicGet(queue: QueueName, autoAck: Boolean): F[client.GetResponse]

  def deliveryStream(
    queue: QueueName,
    prefetchCount: Int,
  ): F[(ConsumerTag, Stream[F, client.Delivery])]
}
