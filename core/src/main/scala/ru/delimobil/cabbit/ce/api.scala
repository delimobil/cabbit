package ru.delimobil.cabbit.ce

private[cabbit] object api {

  trait Semaphore[F[_]] {
    def withPermit[V](action: F[V]): F[V]
  }

  trait SemaphoreMake[F[_]] {
    def make(n: Long): F[Semaphore[F]]
  }

  object SemaphoreMake {
    def apply[F[_]](implicit ev: SemaphoreMake[F]): SemaphoreMake[F] = ev
  }

  trait Blocker[F[_]] {
    def delay[V](f: => V): F[V]
  }
}
