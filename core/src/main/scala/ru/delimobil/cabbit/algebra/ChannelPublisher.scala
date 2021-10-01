package ru.delimobil.cabbit.algebra

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.algebra.ChannelPublisher.MandatoryArgument
import ru.delimobil.cabbit.encoder.BodyEncoder
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.QueueName
import ru.delimobil.cabbit.model.RoutingKey

trait ChannelPublisher[F[_]] extends ShutdownNotifier[F] {

  def basicPublishDirect[V](
      queueName: QueueName,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties()
  )(implicit encoder: BodyEncoder[V]): F[Unit]

  def basicPublishFanout[V](
      exchangeName: ExchangeName,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties()
  )(implicit encoder: BodyEncoder[V]): F[Unit]

  def basicPublish[V](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties()
  )(implicit encoder: BodyEncoder[V]): F[Unit]
}

object ChannelPublisher {

  sealed abstract class MandatoryArgument(val bool: Boolean)

  object MandatoryArgument {
    object Mandatory extends MandatoryArgument(true)
    object NonMandatory extends MandatoryArgument(false)
  }
}
