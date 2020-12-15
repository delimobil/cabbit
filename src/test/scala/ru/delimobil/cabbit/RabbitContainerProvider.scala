package ru.delimobil.cabbit

import cats.effect.Resource
import cats.effect.Sync
import com.dimafeng.testcontainers.RabbitMQContainer
import ru.delimobil.cabbit.config.Fs2RabbitConfig.Host
import ru.delimobil.cabbit.config.Fs2RabbitConfig.Port

class RabbitContainerProvider {

  private val container: RabbitMQContainer = RabbitMQContainer()

  container.container.start()

  val host: Host = container.host

  val port: Port = container.amqpPort
}

object RabbitContainerProvider {

  def resource[F[_]: Sync]: Resource[F, RabbitContainerProvider] =
    Resource {
      Sync[F].delay {
        val provider = new RabbitContainerProvider
        (provider, Sync[F].delay(provider.container.stop()))
      }
    }
}
