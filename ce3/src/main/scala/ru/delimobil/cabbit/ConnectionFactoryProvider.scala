package ru.delimobil.cabbit

import cats.effect.Resource
import cats.effect.kernel.Async
import com.rabbitmq.client.Address
import com.rabbitmq.client.{ConnectionFactory => JConnectionFactory}
import ru.delimobil.cabbit.api.ConnectionFactory
import ru.delimobil.cabbit.client.ConnectionTimeouted
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.model.CabbitConfig

import javax.net.ssl.SSLContext
import scala.concurrent.duration.FiniteDuration

object ConnectionFactoryProvider {

  def provide[F[_]: Async](
      config: CabbitConfig,
      sslContext: Option[SSLContext]
  ): ConnectionFactory[F] =
    provide(sslContext.fold(config.factoryDefaultSsl)(config.factoryExternalSsl))

  def provide[F[_]: Async](factory: JConnectionFactory): ConnectionFactory[F] =
    new RabbitClientConnectionFactory[F](factory)

  def provideTimeouted[F[_]: Async](
      config: CabbitConfig,
      sslContext: Option[SSLContext],
      timeout: FiniteDuration
  ): ConnectionFactory[F] = {
    val factory = sslContext.fold(config.factoryDefaultSsl)(config.factoryExternalSsl)
    provideTimeouted(factory, timeout)
  }

  def provideTimeouted[F[_]: Async](
      factory: JConnectionFactory,
      timeout: FiniteDuration
  ): ConnectionFactory[F] = {
    val delegatee = provide(factory)

    new api.ConnectionFactory[F] {
      def newConnection(
          addresses: List[Address],
          appName: Option[String]
      ): Resource[F, api.Connection[F]] =
        delegatee
          .newConnection(addresses, appName)
          .map(new ConnectionTimeouted(_, timeout))
//          .timeout(timeout) FIXME
    }
  }

}
