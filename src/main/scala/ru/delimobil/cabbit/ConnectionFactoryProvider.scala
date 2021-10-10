package ru.delimobil.cabbit

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import com.rabbitmq.client.{ConnectionFactory => JConnectionFactory}
import javax.net.ssl.SSLContext
import ru.delimobil.cabbit.api.ConnectionFactory
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.model.CabbitConfig

object ConnectionFactoryProvider {

  def provide[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      config: CabbitConfig,
      sslContext: Option[SSLContext]
  ): ConnectionFactory[F] =
    provide(blocker, sslContext.fold(config.factoryDefaultSsl)(config.factoryExternalSsl))

  def provide[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      factory: JConnectionFactory
  ): ConnectionFactory[F] =
    new RabbitClientConnectionFactory[F](blocker, factory)
}
