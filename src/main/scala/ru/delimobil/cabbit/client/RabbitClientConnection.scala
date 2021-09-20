package ru.delimobil.cabbit.client

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.Channel
import ru.delimobil.cabbit.algebra.ChannelConsumer
import ru.delimobil.cabbit.algebra.ChannelDeclaration
import ru.delimobil.cabbit.algebra.ChannelOnPool
import ru.delimobil.cabbit.algebra.ChannelPublisher
import ru.delimobil.cabbit.algebra.Connection
import ru.delimobil.cabbit.client.consumer.RabbitClientConsumerProvider

private[client] final class RabbitClientConnection[F[_]: ConcurrentEffect: ContextShift](
  raw: client.Connection,
  blocker: Blocker,
  consumerProvider: RabbitClientConsumerProvider[F],
) extends Connection[F] {

  def createChannelDeclaration: Resource[F, ChannelDeclaration[F]] =
    createChannel

  def createChannelPublisher: Resource[F, ChannelPublisher[F]] =
    createChannel

  def createChannelConsumer: Resource[F, ChannelConsumer[F]] =
    createChannel

  def createChannel: Resource[F, Channel[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannel[F](ch, consumerProvider))

  def isOpen: F[Boolean] =
    blocker.delay(raw.isOpen)

  private def createChannelOnPool: Resource[F, ChannelOnPool[F]] =
  // Doesn't use Resource.fromAutoCloseable because of custom error handler
    Resource.make(blocker.delay(raw.createChannel()))(channel => blocker.delay(close(channel)))
      .flatMap(channel => Resource.eval(RabbitClientChannelOnPool.make[F](channel, blocker)))

  private def close(channel: client.Channel): Unit =
    try { channel.close() }
    catch { case _: client.AlreadyClosedException => () }
}
