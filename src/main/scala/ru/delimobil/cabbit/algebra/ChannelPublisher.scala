package ru.delimobil.cabbit.algebra

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument

trait ChannelPublisher[F[_]] extends ShutdownNotifier[F] {

  def basicPublishDefaultDirect[V](
    routingKey: RoutingKey,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit]

  def basicPublishDefaultFanout[V](
    exchangeName: ExchangeName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit]

  def basicPublish[V](
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit]
}

object ChannelPublisher {

  sealed abstract class MandatoryArgument(val bool: Boolean)

  object MandatoryArgument {
    object Mandatory extends MandatoryArgument(true)
    object NonMandatory extends MandatoryArgument(false)
  }
}
