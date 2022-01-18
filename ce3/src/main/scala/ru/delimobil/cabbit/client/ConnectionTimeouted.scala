package ru.delimobil.cabbit.client

import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.syntax.temporal._
import com.rabbitmq.client.Connection
import ru.delimobil.cabbit.api
import ru.delimobil.cabbit.ce.impl._

import scala.concurrent.duration.FiniteDuration

final class ConnectionTimeouted[F[_]: Concurrent: Temporal](
    delegatee: api.Connection[F],
    duration: FiniteDuration
) extends api.Connection[F] {

  def createChannelDeclaration: Resource[F, api.ChannelDeclaration[F]] =
    createChannel.timeout(duration)

  def createChannelPublisher: Resource[F, api.ChannelPublisher[F]] =
    createChannel.timeout(duration)

  def createChannelConsumer: Resource[F, api.ChannelConsumer[F]] =
    createChannel.timeout(duration)

  def createChannel: Resource[F, api.Channel[F]] =
    delegatee.createChannel.map(ch => new ChannelTimeouted(ch, duration))

  def delay[V](f: Connection => V): F[V] = delegatee.delay(f).timeout(duration)
}
