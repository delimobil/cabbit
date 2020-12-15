package ru.delimobil.cabbit.client

import cats.Id
import cats.effect.Concurrent
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.~>
import com.rabbitmq.client
import fs2.Stream
import fs2.concurrent.NoneTerminatedQueue
import fs2.concurrent.Queue
import ru.delimobil.cabbit.algebra.ChannelConsumer
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.algebra.ConsumerTag
import ru.delimobil.cabbit.algebra.DeliveryTag
import ru.delimobil.cabbit.algebra.QueueName

final class RabbitClientChannelConsumer[F[_]: Concurrent](
  channelOnPool: ChannelOnPool[F]
) extends ChannelConsumer[F] {

  def basicQos(prefetchCount: Int): F[Unit] = channelOnPool.delay(_.basicQos(prefetchCount))

  def basicConsume(
    queueName: QueueName,
    deliverCallback: client.DeliverCallback,
    cancelCallback: client.CancelCallback
  ): F[ConsumerTag] =
    channelOnPool.delay(_.basicConsume(queueName, deliverCallback, cancelCallback))

  def basicGet(queue: QueueName, autoAck: Boolean): F[client.GetResponse] =
    channelOnPool.delay(_.basicGet(queue, autoAck))

  def deliveryStream(
    queueName: QueueName,
    prefetchCount: Int
  )(implicit eval: F ~> Id): F[(ConsumerTag, Stream[F, client.Delivery])] =
    for {
      _ <- basicQos(prefetchCount)
      queue <- Queue.boundedNoneTerminated[F, client.Delivery](prefetchCount)
      callbacks = getCallbacks(queue)
      (cancel, deliver) = callbacks
      tag <- basicConsume(queueName, deliver, cancel)
    } yield (tag, queue.dequeue)

  private def getCallbacks(
    queue: NoneTerminatedQueue[F, client.Delivery]
  )(implicit eval: F ~> Id): (client.CancelCallback, client.DeliverCallback) = {
    val cancelCallback: client.CancelCallback = _ => eval(queue.enqueue1(none))
    val deliverCallback: client.DeliverCallback = (_, delivery) => eval(queue.enqueue1(delivery.some))
    (cancelCallback, deliverCallback)
  }

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit] =
    channelOnPool.delay(_.basicAck(deliveryTag, multiple))

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    channelOnPool.delay(_.basicNack(deliveryTag, multiple, requeue))

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit] =
    channelOnPool.delay(_.basicReject(deliveryTag, requeue))

  def basicCancel(consumerTag: ConsumerTag): F[Unit] =
    channelOnPool.delay(_.basicCancel(consumerTag))
}
