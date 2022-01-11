package ru.delimobil.cabbit.client

import cats.Functor
import cats.syntax.functor._
import com.rabbitmq.client
import ru.delimobil.cabbit.ce.api.Blocker
import ru.delimobil.cabbit.ce.api.Semaphore
import ru.delimobil.cabbit.ce.api.SemaphoreMake
import ru.delimobil.cabbit.core.ChannelBlocking

private[client] final class RabbitClientChannelWrapped[F[_]](
    semaphore: Semaphore[F],
    channel: client.Channel,
    blocker: Blocker[F]
) extends ChannelBlocking[F] {

  def delay[V](f: client.Channel => V): F[V] =
    semaphore.withPermit(blocker.delay(f(channel)))

  def close: F[Unit] =
    semaphore.withPermit(blocker.delay(closeUnsafe()))

  private def closeUnsafe(): Unit =
    try channel.close()
    catch { case _: client.AlreadyClosedException => () }
}

private[client] object RabbitClientChannelWrapped {

  def make[F[_]: SemaphoreMake: Functor](
      channel: client.Channel,
      blocker: Blocker[F]
  ): F[ChannelBlocking[F]] =
    SemaphoreMake[F].make(1).map(new RabbitClientChannelWrapped[F](_, channel, blocker))
}
