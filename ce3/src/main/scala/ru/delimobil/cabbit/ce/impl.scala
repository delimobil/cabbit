package ru.delimobil.cabbit.ce

import cats.effect.Concurrent
import cats.effect.MonadCancelThrow
import cats.effect.Temporal
import cats.effect.kernel.Sync
import cats.effect.std.{Semaphore => SemaphoreCE3}
import cats.effect.syntax.temporal._
import cats.syntax.functor._
import ru.delimobil.cabbit.ce.api._

import scala.concurrent.duration.FiniteDuration

object impl {

  final class SemaphoreDelegate[F[_]: MonadCancelThrow](
      sem: SemaphoreCE3[F]
  ) extends Semaphore[F] {
    def withPermit[V](action: F[V]): F[V] =
      sem.permit.use(_ => action)
  }

  final class BlockerDelegate[F[_]: Sync] extends Blocker[F] {
    def delay[V](f: => V): F[V] =
      Sync[F].blocking(f)
  }

  implicit def semaphoreMake[F[_]: Concurrent]: SemaphoreMake[F] =
    new SemaphoreMake[F] {
      def make(n: Long): F[Semaphore[F]] =
        SemaphoreCE3(n).map(new SemaphoreDelegate(_))
    }

  implicit def timeouterTemporal[F[_]: Temporal]: Timeouter[F] =
    new Timeouter[F] {
      def timeout[V](action: F[V], duration: FiniteDuration): F[V] = action.timeout(duration)
    }
}
