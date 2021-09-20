package ru.delimobil.cabbit.client

import java.util.concurrent.Executors

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Sync
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.ChannelOnPool

import scala.util.Random

/** @param channel instances must not be shared between threads */
private[client] final class RabbitClientChannelOnPool[F[_]: Sync: ContextShift] (
  channel: client.Channel,
  blocker: Blocker
) extends ChannelOnPool[F] {

  def delay[V](f: client.Channel => V): F[V] =
    blocker.delay(f(channel))

  def blockOn[V](f: client.Channel => F[V]): F[V] =
    blocker.blockOn(f(channel))

  def isOpen: F[Boolean] =
    blocker.delay(channel.isOpen)
}

private[client] object RabbitClientChannelOnPool {

  def make[F[_]: Sync: ContextShift](channel: client.Channel): Resource[F, ChannelOnPool[F]] =
    Blocker
      .fromExecutorService(getChannelExecutor)
      .map(new RabbitClientChannelOnPool(channel, _))

  private def getChannelExecutor[F[_]: Sync] =
    Sync[F].delay(
      Executors.newSingleThreadExecutor(runnable => {
        val thread = new Thread(runnable, s"rabbit-client-channel-${Random.nextInt(1000)}")
        thread.setDaemon(true)
        thread
      })
    )
}
