package ru.delimobil.cabbit.client.consumer

import cats.effect.ConcurrentEffect
import cats.effect.syntax.effect._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.option.none
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import fs2.Stream
import fs2.concurrent.NoneTerminatedQueue
import fs2.concurrent.Queue

private[client] final class QueueNoneTerminatedConsumerProvider[F[_]: ConcurrentEffect]
    extends RabbitClientConsumerProvider[F] {

  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])] =
    Queue
      .boundedNoneTerminated[F, Delivery](prefetchCount)
      .map { queue => (consumer(queue), queue.dequeue) }

  private def consumer(queue: NoneTerminatedQueue[F, Delivery]): Consumer = {
    val cancel: CancelCallback = _ => queue.enqueue1(none).toIO.unsafeRunSync()
    val deliver: DeliverCallback = (_, delivery) => queue.enqueue1(delivery.some).toIO.unsafeRunSync()

    new Consumer {

      def handleConsumeOk(consumerTag: String): Unit = {}

      def handleCancelOk(consumerTag: String): Unit =
        cancel.handle(consumerTag)

      def handleCancel(consumerTag: String): Unit =
        cancel.handle(consumerTag)

      def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
        cancel.handle(consumerTag)

      def handleRecoverOk(consumerTag: String): Unit = {}

      def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: AMQP.BasicProperties,
        body: Array[Byte],
      ): Unit = deliver.handle(consumerTag, new Delivery(envelope, properties, body))
    }
  }
}
