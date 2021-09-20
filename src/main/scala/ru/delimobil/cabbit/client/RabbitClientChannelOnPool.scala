package ru.delimobil.cabbit.client

import cats.effect.Blocker
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.Sync
import cats.effect.concurrent.Semaphore
import cats.syntax.functor._
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.ChannelOnPool

private[client] final class RabbitClientChannelOnPool[F[_]: Sync: ContextShift] (
  semaphore: Semaphore[F],
  channel: client.Channel,
  blocker: Blocker,
) extends ChannelOnPool[F] {

  def delay[V](f: client.Channel => V): F[V] =
    semaphore.withPermit(blocker.delay(f(channel)))

  def blockOn[V](f: client.Channel => F[V]): F[V] =
    semaphore.withPermit(blocker.blockOn(f(channel)))

  def isOpen: F[Boolean] =
    semaphore.withPermit(blocker.delay(channel.isOpen))
}

private[client] object RabbitClientChannelOnPool {

  def make[F[_]: Concurrent: ContextShift](channel: client.Channel, blocker: Blocker): F[ChannelOnPool[F]] =
    Semaphore[F](1).map(new RabbitClientChannelOnPool[F](_, channel, blocker))
}
