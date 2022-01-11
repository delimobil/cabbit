package ru.delimobil.cabbit

import cats.effect.Resource

object api {

  trait ChannelExtendable[F[_]] extends core.ChannelExtendable[F]

  trait ShutdownNotifier[F[_]] extends core.ShutdownNotifier[F]

  trait ChannelAcker[F[_]]
      extends core.ChannelAcker[F]
      with ShutdownNotifier[F]
      with ChannelExtendable[F]

  trait ChannelConsumer[F[_]] extends core.ChannelConsumer[F, fs2.Stream[F, *]] with ChannelAcker[F]

  trait ChannelDeclaration[F[_]]
      extends core.ChannelDeclaration[F]
      with ShutdownNotifier[F]
      with ChannelExtendable[F]

  trait ChannelPublisher[F[_]]
      extends core.ChannelPublisher[F]
      with ShutdownNotifier[F]
      with ChannelExtendable[F]

  trait Channel[F[_]] extends ChannelConsumer[F] with ChannelDeclaration[F] with ChannelPublisher[F]

  trait Connection[F[_]] extends ShutdownNotifier[F] {

    def createChannelDeclaration: Resource[F, ChannelDeclaration[F]]

    def createChannelPublisher: Resource[F, ChannelPublisher[F]]

    def createChannelConsumer: Resource[F, ChannelConsumer[F]]

    def createChannel: Resource[F, Channel[F]]

    def close: F[Unit]
  }

  trait ConnectionFactory[F[_]] {
    def newConnection(
        addresses: List[com.rabbitmq.client.Address],
        appName: Option[String] = None
    ): Resource[F, Connection[F]]
  }
}
