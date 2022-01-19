package ru.delimobil.cabbit.ce

import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.concurrent.{Semaphore => SemaphoreCE2}
import cats.effect.syntax.concurrent._
import cats.effect.{Blocker => BlockerCE2}
import cats.syntax.functor._
import ru.delimobil.cabbit.ce.api.Blocker
import ru.delimobil.cabbit.ce.api.Semaphore
import ru.delimobil.cabbit.ce.api.SemaphoreMake
import ru.delimobil.cabbit.ce.api.Timeouter

import scala.concurrent.duration.FiniteDuration

private[cabbit] object impl {

  final class SemaphoreDelegate[F[_]](sem: SemaphoreCE2[F]) extends Semaphore[F] {
    def withPermit[V](action: F[V]): F[V] =
      sem.withPermit(action)
  }

  final class BlockerDelegate[F[_]: Sync: ContextShift](blocker: BlockerCE2) extends Blocker[F] {
    def delay[V](f: => V): F[V] =
      blocker.delay(f)
  }

  implicit def semaphoreMake[F[_]](implicit c: Concurrent[F]): SemaphoreMake[F] =
    new SemaphoreMake[F] {
      def make(n: Long): F[Semaphore[F]] =
        SemaphoreCE2(n).map(new SemaphoreDelegate(_))
    }

  implicit def timeouterTimer[F[_]: Timer: Concurrent]: Timeouter[F] =
    new Timeouter[F] {
      def timeout[V](action: F[V], duration: FiniteDuration): F[V] = action.timeout(duration)
    }
}
