package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Deferred
import cats.effect.syntax.effect._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.option.none
import cats.syntax.semigroupal._
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

private[client] trait RabbitClientConsumerProvider[F[_]] {
  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])]
}

private[client] object RabbitClientConsumerProvider {

  private final class QueueNoneTerminatedConsumerProvider[F[_]: ConcurrentEffect]
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
          body: Array[Byte]
        ): Unit = deliver.handle(consumerTag, new Delivery(envelope, properties, body))
      }
    }
  }

  private final class QueueDeferredConsumerProvider[F[_]: ConcurrentEffect] extends RabbitClientConsumerProvider[F] {

    def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])] =
      Queue
        .bounded[F, Delivery](prefetchCount)
        .product(Deferred[F, Either[Throwable, Unit]])
        .map { case (queue, deferred) => (consumer(queue, deferred), queue.dequeue.interruptWhen(deferred)) }

    private def consumer(queue: Queue[F, Delivery], deferred: Deferred[F, Either[Throwable, Unit]]): Consumer =
      new Consumer {

        def handleConsumeOk(consumerTag: String): Unit = {}

        def handleCancelOk(consumerTag: String): Unit =
          deferred.complete(().asRight)

        def handleCancel(consumerTag: String): Unit =
          deferred.complete(().asRight)

        def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
          deferred.complete(sig.asLeft)

        def handleRecoverOk(consumerTag: String): Unit = {}

        def handleDelivery(
          consumerTag: String,
          envelope: Envelope,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
        ): Unit = queue.enqueue1(new Delivery(envelope, properties, body)).toIO.unsafeRunSync()
      }
  }

  def instance[F[_]: ConcurrentEffect]: RabbitClientConsumerProvider[F] = new QueueNoneTerminatedConsumerProvider[F]
}
