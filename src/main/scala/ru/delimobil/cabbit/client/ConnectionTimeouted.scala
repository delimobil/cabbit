package ru.delimobil.cabbit.client

import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.syntax.all._
import com.rabbitmq.client.Connection
import ru.delimobil.cabbit.api
import ru.delimobil.cabbit.ce.impl._

import scala.concurrent.duration.FiniteDuration

// Resource in ce2 doesn't have timeout
final class ConnectionTimeouted[F[_]: Concurrent: Timer](
    delegatee: api.Connection[F],
    duration: FiniteDuration
) extends api.Connection[F] {

  def createChannelDeclaration: Resource[F, api.ChannelDeclaration[F]] =
    createChannel

  def createChannelPublisher: Resource[F, api.ChannelPublisher[F]] =
    createChannel

  def createChannelConsumer: Resource[F, api.ChannelConsumer[F]] =
    createChannel

  def createChannel: Resource[F, api.Channel[F]] =
    delegatee.createChannel.map(ch => new ChannelTimeouted(ch, duration))

  def delay[V](f: Connection => V): F[V] = delegatee.delay(f).timeout(duration)
}
