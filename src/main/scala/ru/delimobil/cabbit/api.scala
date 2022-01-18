package ru.delimobil.cabbit

import cats.effect.Resource
import fs2.Stream

object api {

  trait ChannelExtendable[F[_]] extends core.ChannelExtendable[F]

  trait ConnectionExtendable[F[_]] extends core.ConnectionExtendable[F]

  trait ChannelAcker[F[_]] extends core.ChannelAcker[F] with ChannelExtendable[F]

  trait ChannelConsumer[F[_]] extends core.ChannelConsumer[F, Stream[F, *]] with ChannelAcker[F]

  trait ChannelDeclaration[F[_]] extends core.ChannelDeclaration[F] with ChannelExtendable[F]

  trait ChannelPublisher[F[_]] extends core.ChannelPublisher[F] with ChannelExtendable[F]

  trait Channel[F[_]]
      extends core.Channel[F, Stream[F, *]]
      with ChannelConsumer[F]
      with ChannelDeclaration[F]
      with ChannelPublisher[F]

  trait Connection[F[_]] extends ConnectionExtendable[F] {

    def createChannelDeclaration: Resource[F, ChannelDeclaration[F]]

    def createChannelPublisher: Resource[F, ChannelPublisher[F]]

    def createChannelConsumer: Resource[F, ChannelConsumer[F]]

    def createChannel: Resource[F, Channel[F]]
  }

  trait ConnectionFactory[F[_]] {
    def newConnection(
        addresses: List[com.rabbitmq.client.Address],
        appName: Option[String] = None
    ): Resource[F, Connection[F]]
  }
}
