package ru.delimobil.cabbit.core

import com.rabbitmq.client
import ru.delimobil.cabbit.model.ConsumerTag
import ru.delimobil.cabbit.model.QueueName

private[cabbit] trait ChannelConsumer[F[_], S[_]] {

  def basicQos(prefetchCount: Int): F[Unit]

  def basicConsume(
      queue: QueueName,
      deliverCallback: client.DeliverCallback,
      cancelCallback: client.CancelCallback
  ): F[ConsumerTag]

  def basicConsume(queue: QueueName, callback: client.Consumer): F[ConsumerTag]

  def basicGet(queue: QueueName, autoAck: Boolean): F[client.GetResponse]

  def deliveryStream(
      queue: QueueName,
      prefetchCount: Int
  ): F[(ConsumerTag, S[client.Delivery])]

  def basicCancel(consumerTag: ConsumerTag): F[Unit]
}
