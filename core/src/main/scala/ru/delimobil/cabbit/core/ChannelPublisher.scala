package ru.delimobil.cabbit.core

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.encoder.BodyEncoder
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.MandatoryArgument
import ru.delimobil.cabbit.model.QueueName
import ru.delimobil.cabbit.model.RoutingKey

private[cabbit] trait ChannelPublisher[F[_]] {

  def basicPublishDirect[V](
      queueName: QueueName,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties
  )(implicit encoder: BodyEncoder[V]): F[Unit]

  def basicPublishFanout[V](
      exchangeName: ExchangeName,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties
  )(implicit encoder: BodyEncoder[V]): F[Unit]

  def basicPublish[V](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties
  )(implicit encoder: BodyEncoder[V]): F[Unit]
}
