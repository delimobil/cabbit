package ru.delimobil.cabbit.client

import cats.Functor
import cats.syntax.functor._
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.ce.api.Blocker
import ru.delimobil.cabbit.ce.api.Semaphore
import ru.delimobil.cabbit.ce.api.SemaphoreMake

private[client] final class RabbitClientChannelOnPool[F[_]](
    semaphore: Semaphore[F],
    channel: client.Channel,
    blocker: Blocker[F]
) extends ChannelOnPool[F] {

  def delay[V](f: client.Channel => V): F[V] =
    semaphore.withPermit(blocker.delay(f(channel)))

  def isOpen: F[Boolean] =
    semaphore.withPermit(blocker.delay(channel.isOpen))

  def close: F[Unit] =
    semaphore.withPermit(blocker.delay(closeUnsafe()))

  private def closeUnsafe(): Unit =
    try channel.close()
    catch { case _: client.AlreadyClosedException => () }
}

private[client] object RabbitClientChannelOnPool {

  def make[F[_]: SemaphoreMake: Functor](
      channel: client.Channel,
      blocker: Blocker[F]
  ): F[ChannelOnPool[F]] =
    SemaphoreMake[F].make(1).map(new RabbitClientChannelOnPool[F](_, channel, blocker))
}
