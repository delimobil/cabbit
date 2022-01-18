package ru.delimobil.cabbit.client

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.syntax.functor._
import cats.syntax.semigroupal._
import com.rabbitmq.client
import com.rabbitmq.client.Connection
import ru.delimobil.cabbit.CollectionConverters._
import ru.delimobil.cabbit.api
import ru.delimobil.cabbit.ce.impl.BlockerDelegate
import ru.delimobil.cabbit.ce.impl._
import ru.delimobil.cabbit.client.consumer.ChannelDeferredConsumerProvider

private[cabbit] final class RabbitClientConnectionFactory[F[_]: Async](
    factory: client.ConnectionFactory
) extends api.ConnectionFactory[F] {

  def newConnection(
      addresses: List[client.Address],
      appName: Option[String] = None
  ): Resource[F, api.Connection[F]] =
    Sync[F]
      .blocking(factory.newConnection(addresses.asJava, appName.orNull))
      .map(new RabbitClientConnectionBlocker[F](_, new BlockerDelegate[F]))
      .pipe(Resource.make(_)(_.close))
      .product(ChannelDeferredConsumerProvider.make[F])
      .map { case (delegate, consumerProvider) =>
        val createChannelR = Resource(delegate.channel)
        val channelR = createChannelR.map(ch => new RabbitClientChannel(ch, consumerProvider))

        new api.Connection[F] {
          def createChannelDeclaration: Resource[F, api.ChannelDeclaration[F]] = channelR
          def createChannelPublisher: Resource[F, api.ChannelPublisher[F]] = channelR
          def createChannelConsumer: Resource[F, api.ChannelConsumer[F]] = channelR
          def createChannel: Resource[F, api.Channel[F]] = channelR
          def delay[V](f: Connection => V): F[V] = delegate.delay(f)
        }
      }
}
