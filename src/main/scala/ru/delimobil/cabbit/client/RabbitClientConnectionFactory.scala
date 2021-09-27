package ru.delimobil.cabbit.client

import cats.effect.{Blocker => BlockerCE2}
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.syntax.functor._
import com.rabbitmq.client
import fs2.Stream
import ru.delimobil.cabbit.algebra.Connection
import ru.delimobil.cabbit.algebra.ConnectionFactory
import ru.delimobil.cabbit.client.consumer.QueueDeferredConsumerProvider
import ru.delimobil.cabbit.client.poly.RabbitClientConsumerProvider

import ru.delimobil.cabbit.CollectionConverters._

private[cabbit] final class RabbitClientConnectionFactory[F[_]: ConcurrentEffect: ContextShift](
  blocker: BlockerCE2,
  factory: client.ConnectionFactory
) extends ConnectionFactory[F] {

  private val consumerProvider: RabbitClientConsumerProvider[F, Stream] = new QueueDeferredConsumerProvider[F]

  def newConnection(addresses: List[client.Address], appName: Option[String] = None): Resource[F, Connection[F]] =
    blocker
      .delay(factory.newConnection(addresses.asJava, appName.orNull))
      .map(new RabbitClientConnection[F](_ , blocker, consumerProvider))
      .pipe(Resource.make(_)(_.close))
}
