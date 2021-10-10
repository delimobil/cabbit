package ru.delimobil.cabbit.client

import cats.effect.Resource
import cats.effect.kernel.Async
import com.rabbitmq.client
import fs2.Stream
import ru.delimobil.cabbit.api.Channel
import ru.delimobil.cabbit.api.ChannelConsumer
import ru.delimobil.cabbit.api.ChannelDeclaration
import ru.delimobil.cabbit.api.ChannelPublisher
import ru.delimobil.cabbit.api.Connection
import ru.delimobil.cabbit.ce.impl._
import ru.delimobil.cabbit.client.poly.RabbitClientConsumerProvider

private[client] final class RabbitClientConnection[F[_]: Async](
    raw: client.Connection,
    consumerProvider: RabbitClientConsumerProvider[F, Stream]
) extends Connection[F] {

  private val blocker = new BlockerDelegate[F]

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
