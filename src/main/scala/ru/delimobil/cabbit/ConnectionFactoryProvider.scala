package ru.delimobil.cabbit

import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import com.rabbitmq.client.{ConnectionFactory => JConnectionFactory}
import javax.net.ssl.SSLContext
import ru.delimobil.cabbit.algebra.ConnectionFactory
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.config.CabbitConfig

object ConnectionFactoryProvider {

  def provide[F[_]: ConcurrentEffect: ContextShift](
    config: CabbitConfig,
    context: Option[SSLContext]
  ): ConnectionFactory[F] =
    provide(context.fold(config.factoryDefaultSsl)(config.factoryExternalSsl))

  def provide[F[_]: ConcurrentEffect: ContextShift](factory: => JConnectionFactory): ConnectionFactory[F] =
    new RabbitClientConnectionFactory[F](factory)
}
