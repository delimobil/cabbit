package ru.delimobil.cabbit

import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import ru.delimobil.cabbit.algebra.ConnectionFactory
import ru.delimobil.cabbit.client.RabbitClientConnectionFactory
import ru.delimobil.cabbit.config.CabbitConfig

object ConnectionFactoryProvider {

  def provide[F[_]: ConcurrentEffect: ContextShift](config: CabbitConfig): ConnectionFactory[F] =
    new RabbitClientConnectionFactory(config)
}
