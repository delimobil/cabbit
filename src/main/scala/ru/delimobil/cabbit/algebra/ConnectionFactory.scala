package ru.delimobil.cabbit.algebra

import cats.effect.Resource

trait ConnectionFactory[F[_]] {

  def newConnection: Resource[F, Connection[F]]
}
