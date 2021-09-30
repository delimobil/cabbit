package ru.delimobil.cabbit.client

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.syntax.functor._
import com.rabbitmq.client
import ru.delimobil.cabbit.CollectionConverters._
import ru.delimobil.cabbit.algebra.Connection
import ru.delimobil.cabbit.algebra.ConnectionFactory
import ru.delimobil.cabbit.client.consumer.ChannelDeferredConsumerProvider

private[cabbit] final class RabbitClientConnectionFactory[F[_]: Async](
    factory: client.ConnectionFactory
) extends ConnectionFactory[F] {

  def newConnection(
      addresses: List[client.Address],
      appName: Option[String] = None
  ): Resource[F, Connection[F]] =
    ChannelDeferredConsumerProvider.make[F].flatMap { consumerProvider =>
      Sync[F]
        .delay(factory.newConnection(addresses.asJava, appName.orNull))
        .map(new RabbitClientConnection[F](_, consumerProvider))
        .pipe(Resource.make(_)(_.close))
    }
}
