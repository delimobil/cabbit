package ru.delimobil.cabbit.api

import cats.effect.Resource

trait Connection[F[_]] extends ShutdownNotifier[F] {

  def createChannelDeclaration: Resource[F, ChannelDeclaration[F]]

  def createChannelPublisher: Resource[F, ChannelPublisher[F]]

  def createChannelConsumer: Resource[F, ChannelConsumer[F]]

  def createChannel: Resource[F, Channel[F]]

  def close: F[Unit]
}
