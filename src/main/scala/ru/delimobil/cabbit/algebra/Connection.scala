package ru.delimobil.cabbit.algebra

import cats.effect.Resource

trait Connection[F[_]] {

  def createChannelDeclaration: Resource[F, ChannelDeclaration[F]]

  def createChannelPublisher: Resource[F, ChannelPublisher[F]]

  def createChannelConsumer: Resource[F, ChannelConsumer[F]]
}
