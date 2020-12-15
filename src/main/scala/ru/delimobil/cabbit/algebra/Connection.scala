package ru.delimobil.cabbit.algebra

import cats.effect.Resource

trait Connection[F[_]] {

  def createChannelOnPool: Resource[F, ChannelOnPool[F]]

  def channelDeclaration(channel: ChannelOnPool[F]): ChannelDeclaration[F]

  def channelPublisher(channel: ChannelOnPool[F]): ChannelPublisher[F]

  def channelConsumer(channel: ChannelOnPool[F]): ChannelConsumer[F]
}
