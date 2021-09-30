package ru.delimobil.cabbit.client.consumer

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Deferred
import cats.effect.syntax.effect._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.semigroupal._
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import fs2.Stream
import fs2.concurrent.NoneTerminatedQueue
import fs2.concurrent.Queue
import ru.delimobil.cabbit.client.poly.RabbitClientConsumerProvider

private[client] final class QueueDeferredConsumerProvider[F[_]: ConcurrentEffect]
  extends RabbitClientConsumerProvider[F, Stream] {

  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])] =
    Queue
      .boundedNoneTerminated[F, Delivery](prefetchCount)
      .product(Deferred[F, Either[Throwable, Unit]])
      .map { case (queue, deferred) => (consumer(queue, deferred), queue.dequeue.interruptWhen(deferred)) }

  private def consumer(queue: NoneTerminatedQueue[F, Delivery], deferred: Deferred[F, Either[Throwable, Unit]]): Consumer = {
    def propagate(sig: ShutdownSignalException): Unit = deferred.complete(sig.asLeft).toIO.unsafeRunSync()
    def close(): Unit = queue.enqueue1(none).toIO.unsafeRunSync()
    def send(delivery: Delivery): Unit = queue.enqueue1(delivery.some).toIO.unsafeRunSync()

    new Consumer {

      def handleConsumeOk(consumerTag: String): Unit = {}

      def handleCancelOk(consumerTag: String): Unit =
        close()

      def handleCancel(consumerTag: String): Unit =
        close()

      def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
        propagate(sig)

      def handleRecoverOk(consumerTag: String): Unit = {}

      def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: AMQP.BasicProperties,
        body: Array[Byte]
      ): Unit = send(new Delivery(envelope, properties, body))
    }
  }
}
