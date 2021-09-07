package ru.delimobil.cabbit.algebra

import cats.effect.Resource

trait ConnectionFactory[F[_]] {

  def newConnection(appName: Option[String]): Resource[F, Connection[F]]
}
