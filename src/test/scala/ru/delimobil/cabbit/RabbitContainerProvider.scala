package ru.delimobil.cabbit

import cats.effect.Resource
import cats.effect.Sync
import com.dimafeng.testcontainers.RabbitMQContainer
import ru.delimobil.cabbit.config.CabbitConfig.Host
import ru.delimobil.cabbit.config.CabbitConfig.Port

class RabbitContainerProvider private {

  private val container: RabbitMQContainer = RabbitMQContainer()

  container.container.start()

  val host: Host = container.host

  val port: Port = container.amqpPort
}

object RabbitContainerProvider {

  def resource[F[_]: Sync]: Resource[F, RabbitContainerProvider] =
    Resource.make(Sync[F].delay(new RabbitContainerProvider))(provider => Sync[F].delay(provider.container.stop()))
}
