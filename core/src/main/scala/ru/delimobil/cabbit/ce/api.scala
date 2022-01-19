package ru.delimobil.cabbit.ce

import scala.concurrent.duration.FiniteDuration

private[cabbit] object api {

  trait Semaphore[F[_]] {
    def withPermit[V](action: F[V]): F[V]
  }

  trait SemaphoreMake[F[_]] {
    def make(n: Long): F[Semaphore[F]]
  }

  trait Blocker[F[_]] {
    def delay[V](f: => V): F[V]
  }

  trait Timeouter[F[_]] {
    def timeout[V](action: F[V], dur: FiniteDuration): F[V]
  }
}
