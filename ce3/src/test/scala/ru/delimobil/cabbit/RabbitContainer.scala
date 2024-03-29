package ru.delimobil.cabbit

import cats.data.NonEmptyList
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.functor._
import com.dimafeng.testcontainers.RabbitMQContainer
import ru.delimobil.cabbit.api.Connection
import ru.delimobil.cabbit.model.CabbitConfig
import ru.delimobil.cabbit.model.CabbitConfig.Host
import ru.delimobil.cabbit.model.CabbitConfig.Port

class RabbitContainer private {

  private val container: RabbitMQContainer = RabbitMQContainer()

  container.container.start()

  val host: Host = container.host

  val port: Port = container.amqpPort

  def makeConnection[F[_]: Async]: Resource[F, Connection[F]] = {
    val nodes = NonEmptyList.one(CabbitConfig.CabbitNodeConfig(host, port))
    val config = CabbitConfig(nodes, virtualHost = "/")
    val connectionFactory = ConnectionFactoryProvider.provide[F](config, sslContext = None)
    connectionFactory.newConnection(config.addresses, appName = None)
  }
}

object RabbitContainer {

  def apply[F[_]: Sync]: F[(RabbitContainer, F[Unit])] =
    Sync[F]
      .delay(new RabbitContainer)
      .map(provider => (provider, Sync[F].delay(provider.container.stop())))

  def make[F[_]: Sync]: Resource[F, RabbitContainer] =
    Resource.apply(apply[F])
}
