package ru.delimobil.cabbit.client

import java.util.concurrent.Executors

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Sync
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.ChannelConsumer
import ru.delimobil.cabbit.algebra.ChannelDeclaration
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.algebra.ChannelPublisher
import ru.delimobil.cabbit.algebra.Connection

final class RabbitClientConnection[F[_]: ConcurrentEffect: ContextShift](
  raw: client.Connection
) extends Connection[F] {

  def createChannelOnPool: Resource[F, ChannelOnPool[F]] =
    for {
      blocker <- Blocker.fromExecutorService(Sync[F].delay(getChannelExecutor))
      rawChannel <- Resource.make(blocker.delay(raw.createChannel()))(channel => blocker.delay(channel.close()))
    } yield new RabbitClientChannelOnPool(rawChannel, blocker)

  def channelDeclaration(channel: ChannelOnPool[F]): ChannelDeclaration[F] =
    new RabbitClientChannelDeclaration[F](channel)

  def channelPublisher(channel: ChannelOnPool[F]): ChannelPublisher[F] =
    new RabbitClientChannelPublisher[F](channel)

  def channelConsumer(channel: ChannelOnPool[F]): ChannelConsumer[F] =
    new RabbitClientChannelConsumer[F](channel)

  private def getChannelExecutor =
    Executors.newSingleThreadExecutor(
      runnable => {
        val thread = new Thread(runnable, s"rabbit-client-channel-${math.abs(hashCode)}")
        thread.setDaemon(true)
        thread
      }
    )
}
