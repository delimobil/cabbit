package ru.delimobil.cabbit.client

import java.util.concurrent.Executors

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Sync
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.Channel
import ru.delimobil.cabbit.algebra.ChannelConsumer
import ru.delimobil.cabbit.algebra.ChannelDeclaration
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.algebra.ChannelPublisher
import ru.delimobil.cabbit.algebra.Connection

final class RabbitClientConnection[F[_]: ConcurrentEffect: ContextShift](
  raw: client.Connection
) extends Connection[F] {

  private val getChannelExecutor =
    Sync[F].delay(
      Executors.newSingleThreadExecutor(runnable => {
        val thread = new Thread(runnable, s"rabbit-client-channel-${math.abs(hashCode)}")
        thread.setDaemon(true)
        thread
      })
    )

  def createChannelDeclaration: Resource[F, ChannelDeclaration[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannelDeclaration[F](ch))

  def createChannelPublisher: Resource[F, ChannelPublisher[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannelPublisher[F](ch))

  def createChannelConsumer: Resource[F, ChannelConsumer[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannelConsumer[F](ch))

  def createChannel: Resource[F, Channel[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannel[F](ch))

  /* blocks on the thread :( */
  def isOpen: F[Boolean] =
    Sync[F].delay(raw.isOpen)

  private def createChannelOnPool: Resource[F, ChannelOnPool[F]] =
    for {
      blocker <- Blocker.fromExecutorService(getChannelExecutor)
      // Doesn't use Resource.fromAutoCloseable because of custom error handler
      acquire = blocker.blockOn(Sync[F].delay(raw.createChannel()))
      rawChannel <- Resource.make(acquire)(channel => blocker.delay(closeChannel(channel)))
    } yield new RabbitClientChannelOnPool(rawChannel, blocker)

  private def closeChannel(ch: client.Channel): Unit =
    try { ch.close() } catch { case _: client.AlreadyClosedException => () }
}
