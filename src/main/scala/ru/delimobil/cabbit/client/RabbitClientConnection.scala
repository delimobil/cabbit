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

final class RabbitClientConnection[F[_]: ConcurrentEffect: ContextShift](
  raw: client.Connection,
  blocker: Blocker,
  consumerProvider: RabbitClientConsumerProvider[F],
) extends Connection[F] {

  def createChannelDeclaration: Resource[F, ChannelDeclaration[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannelDeclaration[F](ch))

  def createChannelPublisher: Resource[F, ChannelPublisher[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannelPublisher[F](ch))

  def createChannelConsumer: Resource[F, ChannelConsumer[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannelConsumer[F](ch, consumerProvider))

  def createChannel: Resource[F, Channel[F]] =
    createChannelOnPool.map(ch => new RabbitClientChannel[F](ch, consumerProvider))

  def isOpen: F[Boolean] =
    blocker.delay(raw.isOpen)

  private def createChannelOnPool: Resource[F, ChannelOnPool[F]] =
    // Doesn't use Resource.fromAutoCloseable because of custom error handler
    Resource
      .make(blocker.delay(raw.createChannel()))(channel => blocker.delay(closeChannel(channel)))
      .flatMap(RabbitClientChannelOnPool.make[F])

  private def closeChannel(ch: client.Channel): Unit =
    try { ch.close() }
    catch { case _: client.AlreadyClosedException => () }
}
