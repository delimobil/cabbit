package ru.delimobil.cabbit.ce

import cats.effect.kernel.GenConcurrent
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Sync
import cats.effect.std.{Semaphore => SemaphoreCE3}
import cats.syntax.functor._
import ru.delimobil.cabbit.ce.api._

object impl {

  //   [F[_]: MonadCancel[*[_], Throwable]] this one didn't compile for scala 3
  final class SemaphoreDelegate[F[_]](sem: SemaphoreCE3[F])(implicit c: MonadCancel[F, Throwable])
      extends Semaphore[F] {
    def withPermit[V](action: F[V]): F[V] =
      sem.permit.use(_ => action)
  }

  final class BlockerDelegate[F[_]: Sync] extends Blocker[F] {
    def delay[V](f: => V): F[V] =
      Sync[F].blocking(f)
  }

  implicit def semaphoreMake[F[_]](implicit gc: GenConcurrent[F, Throwable]): SemaphoreMake[F] =
    new SemaphoreMake[F] {
      def make(n: Long): F[Semaphore[F]] =
        SemaphoreCE3(n).map(new SemaphoreDelegate(_))
    }
}
