package ru.delimobil.cabbit

import cats.effect.kernel.Async
import com.rabbitmq.client.{ConnectionFactory => JConnectionFactory}
import javax.net.ssl.SSLContext
import ru.delimobil.cabbit.api.ConnectionFactory
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.model.CabbitConfig

object ConnectionFactoryProvider {

  def provide[F[_]: Async](
      config: CabbitConfig,
      sslContext: Option[SSLContext]
  ): ConnectionFactory[F] =
    provide(sslContext.fold(config.factoryDefaultSsl)(config.factoryExternalSsl))

  def provide[F[_]: Async](factory: JConnectionFactory): ConnectionFactory[F] =
    new RabbitClientConnectionFactory[F](factory)
}
