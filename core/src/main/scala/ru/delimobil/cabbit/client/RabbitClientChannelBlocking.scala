package ru.delimobil.cabbit.client

import cats.Functor
import cats.MonadThrow
import cats.syntax.applicativeError._
import cats.syntax.functor._
import com.rabbitmq.client
import ru.delimobil.cabbit.ce.api._
import ru.delimobil.cabbit.core.ChannelBlocking

private[client] final class RabbitClientChannelBlocking[F[_]: MonadThrow](
    semaphore: Semaphore[F],
    channel: client.Channel,
    blocker: Blocker[F]
) extends ChannelBlocking[F] {

  def delay[V](f: client.Channel => V): F[V] =
    semaphore.withPermit(blocker.delay(f(channel)))

  def close: F[Unit] = {
    val action = blocker.delay(channel.close())
    semaphore.withPermit(action.recover { case _: client.AlreadyClosedException => () })
  }
}

private[client] object RabbitClientChannelBlocking {

  def make[F[_]](channel: client.Channel, blocker: Blocker[F])(implicit
      m: MonadThrow[F],
      mk: SemaphoreMake[F],
      func: Functor[F]
  ): F[(ChannelBlocking[F], F[Unit])] =
    mk.make(1).map { semaphore =>
      val rabbitClient = new RabbitClientChannelBlocking[F](semaphore, channel, blocker)
      (rabbitClient, rabbitClient.close)
    }
}
