package ru.delimobil.cabbit.algebra

import cats.Id
import cats.~>
import com.rabbitmq.client
import com.rabbitmq.client.GetResponse
import fs2.Stream

trait ChannelConsumer[F[_]] extends ChannelAcker[F] {

  def basicQos(prefetchCount: Int): F[Unit]

  def basicConsume(
    queueName: QueueName,
    deliverCallback: client.DeliverCallback,
    cancelCallback: client.CancelCallback
  ): F[ConsumerTag]

  def basicGet(queue: QueueName, autoAck: Boolean): F[GetResponse]

  def deliveryStream(
    queueName: QueueName,
    prefetchCount: Int
  )(implicit eval: F ~> Id): F[(ConsumerTag, Stream[F, client.Delivery])]
}
