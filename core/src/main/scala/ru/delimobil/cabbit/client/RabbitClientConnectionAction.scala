package ru.delimobil.cabbit.client

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.rabbitmq.client
import ru.delimobil.cabbit.core.ChannelBlocking
import ru.delimobil.cabbit.ce.api.Blocker
import ru.delimobil.cabbit.ce.api.SemaphoreMake

private[client] final class RabbitClientConnectionAction[F[_]: Monad: SemaphoreMake](
    raw: client.Connection,
    blocker: Blocker[F]
) {

  def close: F[Unit] =
    blocker.delay(closeUnsafe())

  def isOpen: F[Boolean] =
    blocker.delay(raw.isOpen)

  def createChannelOnPool: F[(ChannelBlocking[F], F[Unit])] =
    blocker
      .delay(raw.createChannel())
      .flatMap(channel => RabbitClientChannelWrapped.make[F](channel, blocker))
      .fproduct(_.close)

  private def closeUnsafe(): Unit =
    try raw.close()
    catch { case _: client.AlreadyClosedException => () }
}
