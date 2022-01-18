package ru.delimobil.cabbit

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Timer
import com.rabbitmq.client.Address
import com.rabbitmq.client.{ConnectionFactory => JConnectionFactory}
import ru.delimobil.cabbit.api.ConnectionFactory
import ru.delimobil.cabbit.client.ConnectionTimeouted
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.model.CabbitConfig

import javax.net.ssl.SSLContext
import scala.concurrent.duration.FiniteDuration

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

  def provideTimeouted[F[_]: ConcurrentEffect: ContextShift: Timer](
      blocker: Blocker,
      config: CabbitConfig,
      sslContext: Option[SSLContext],
      timeout: FiniteDuration
  ): ConnectionFactory[F] = {
    val factory = sslContext.fold(config.factoryDefaultSsl)(config.factoryExternalSsl)
    provideTimeouted(blocker, factory, timeout)
  }

  def provideTimeouted[F[_]: ConcurrentEffect: ContextShift: Timer](
      blocker: Blocker,
      factory: JConnectionFactory,
      timeout: FiniteDuration
  ): ConnectionFactory[F] = {
    val delegatee = provide(blocker, factory)

    new api.ConnectionFactory[F] {
      def newConnection(
          addresses: List[Address],
          appName: Option[String]
      ): Resource[F, api.Connection[F]] =
        delegatee.newConnection(addresses, appName).map(new ConnectionTimeouted(_, timeout))
    }
  }

}
