package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.{Blocker => BlockerCE2}
import cats.syntax.functor._
import com.rabbitmq.client
import com.rabbitmq.client.Connection
import fs2.Stream
import ru.delimobil.cabbit.CollectionConverters._
import ru.delimobil.cabbit.api
import ru.delimobil.cabbit.ce.impl.BlockerDelegate
import ru.delimobil.cabbit.ce.impl._
import ru.delimobil.cabbit.client.consumer.QueueDeferredConsumerProvider

private[cabbit] final class RabbitClientConnectionFactory[F[_]: ConcurrentEffect: ContextShift](
    blocker: BlockerCE2,
    factory: client.ConnectionFactory
) extends api.ConnectionFactory[F] {

  private val consumerProvider: RabbitClientConsumerProvider[F, Stream[F, *]] =
    new QueueDeferredConsumerProvider[F]

  def newConnection(
      addresses: List[client.Address],
      appName: Option[String] = None
  ): Resource[F, api.Connection[F]] =
    blocker
      .delay(factory.newConnection(addresses.asJava, appName.orNull))
      .map(new RabbitClientConnectionBlocker[F](_, new BlockerDelegate[F](blocker)))
      .pipe(Resource.make(_)(_.close))
      .map { delegate =>
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
