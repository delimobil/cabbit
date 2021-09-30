package ru.delimobil.cabbit.client.consumer

import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.semigroupal._
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import fs2.Stream
import fs2.concurrent.Channel
import ru.delimobil.cabbit.client.poly.RabbitClientConsumerProvider

private[client] final class ChannelDeferredConsumerProvider[F[_]: Async](
    dispatcher: Dispatcher[F]
) extends RabbitClientConsumerProvider[F, Stream] {

  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])] =
    Channel
      .bounded[F, Delivery](prefetchCount)
      .product(Deferred[F, Either[Throwable, Unit]])
      .map { case (channel, deferred) =>
        (consumer(channel, deferred), channel.stream.interruptWhen(deferred))
      }

  private def consumer(
      channel: Channel[F, Delivery],
      deferred: Deferred[F, Either[Throwable, Unit]]
  ): Consumer = {
    def close(): Unit = dispatcher.unsafeRunSync(channel.close)
    def send(delivery: Delivery): Unit = dispatcher.unsafeRunSync(channel.send(delivery))
    def raise(sig: ShutdownSignalException): Unit =
      dispatcher.unsafeRunSync(deferred.complete(sig.asLeft))

    new Consumer {

      def handleConsumeOk(consumerTag: String): Unit = {}

      def handleCancelOk(consumerTag: String): Unit =
        close()

      def handleCancel(consumerTag: String): Unit =
        close()

      def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
        raise(sig)

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

object ChannelDeferredConsumerProvider {

  def make[F[_]: Async]: Resource[F, ChannelDeferredConsumerProvider[F]] =
    Dispatcher[F].map(new ChannelDeferredConsumerProvider[F](_))
}
