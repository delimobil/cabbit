package ru.delimobil.cabbit.client

import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.{Blocker => BlockerCE2}
import com.rabbitmq.client
import fs2.Stream
import ru.delimobil.cabbit.api.Channel
import ru.delimobil.cabbit.api.ChannelConsumer
import ru.delimobil.cabbit.api.ChannelDeclaration
import ru.delimobil.cabbit.api.ChannelPublisher
import ru.delimobil.cabbit.api.Connection
import ru.delimobil.cabbit.ce.impl._

private[client] final class RabbitClientConnection[F[_]: ConcurrentEffect: ContextShift](
    raw: client.Connection,
    blockerCE2: BlockerCE2,
    consumerProvider: RabbitClientConsumerProvider[F, Stream[F, *]]
) extends Connection[F] {

  private val blocker = new BlockerDelegate[F](blockerCE2)

  private val delegate = new RabbitClientConnectionAction[F](raw, blocker)

  def createChannelDeclaration: Resource[F, ChannelDeclaration[F]] =
    createChannel

  def createChannelPublisher: Resource[F, ChannelPublisher[F]] =
    createChannel

  def createChannelConsumer: Resource[F, ChannelConsumer[F]] =
    createChannel

  def createChannel: Resource[F, Channel[F]] =
    Resource(delegate.createChannelOnPool).map(ch => new RabbitClientChannel(ch, consumerProvider))

  def close: F[Unit] =
    delegate.close

  def isOpen: F[Boolean] =
    delegate.isOpen
}
