package ru.delimobil.cabbit

import cats.effect.Concurrent
import cats.effect.ContextShift
import ru.delimobil.cabbit.algebra.ConnectionFactory
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.config.Fs2RabbitConfig

object ConnectionFactoryProvider {

  def provide[F[_]: Concurrent: ContextShift](config: Fs2RabbitConfig): ConnectionFactory[F] =
    new RabbitClientConnectionFactory(config)
}
