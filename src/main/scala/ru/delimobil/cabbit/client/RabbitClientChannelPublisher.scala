package ru.delimobil.cabbit.client

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.algebra._
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument

final class RabbitClientChannelPublisher[F[_]](
  channelOnPool: ChannelOnPool[F],
) extends ChannelPublisher[F] {

  def basicPublishDirect[V](
    queueName: QueueName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    basicPublish(ExchangeNameDefault, RoutingKey(queueName.name), body, mandatory, properties)

  def basicPublishFanout[V](
    exchangeName: ExchangeName,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    basicPublish(exchangeName, RoutingKeyDefault, body, mandatory, properties)

  def basicPublish[V](
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    body: V,
    mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
    properties: BasicProperties = new BasicProperties(),
  )(implicit encoder: BodyEncoder[V]): F[Unit] = {
    val props = encoder.alterProps(properties)
    channelOnPool.delay(_.basicPublish(exchangeName.name, routingKey.name, mandatory.bool, props, encoder.encode(body)))
  }

  def isOpen: F[Boolean] =
    channelOnPool.isOpen
}
