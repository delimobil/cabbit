package ru.delimobil.cabbit.client

import cats.MonadThrow
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import com.rabbitmq.client
import ru.delimobil.cabbit.ce.api._

private[client] final class RabbitClientConnectionBlocker[F[_]: MonadThrow: SemaphoreMake](
    raw: client.Connection,
    blocker: Blocker[F]
) {

  def channel: F[RabbitClientChannelBlocking[F]] = {
    val channel = blocker.delay(raw.createChannel())
    channel.flatMap(RabbitClientChannelBlocking.make[F](_, blocker))
  }

  def delay[V](f: client.Connection => V): F[V] = blocker.delay(f(raw))

  def close: F[Unit] =
    blocker.delay(raw.close()).recover { case _: client.AlreadyClosedException => () }
}
