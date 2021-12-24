package ru.delimobil.cabbit.client.poly

import cats.FlatMap
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.rabbitmq.client
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP.Exchange
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.Method
import ru.delimobil.cabbit.CollectionConverters._
import ru.delimobil.cabbit.api.ChannelAcker._
import ru.delimobil.cabbit.api.ChannelPublisher.MandatoryArgument
import ru.delimobil.cabbit.api._
import ru.delimobil.cabbit.api.poly.ChannelConsumer
import ru.delimobil.cabbit.encoder.BodyEncoder
import ru.delimobil.cabbit.model.ConsumerTag
import ru.delimobil.cabbit.model.DeliveryTag
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.QueueName
import ru.delimobil.cabbit.model.RoutingKey
import ru.delimobil.cabbit.model.declaration.BindDeclaration
import ru.delimobil.cabbit.model.declaration.Declaration
import ru.delimobil.cabbit.model.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.model.declaration.QueueDeclaration

private[client] class RabbitClientChannel[F[_]: FlatMap, Stream[*[_], _]](
    channel: ChannelOnPool[F],
    consumerProvider: RabbitClientConsumerProvider[F, Stream]
) extends ChannelDeclaration[F]
    with ChannelPublisher[F]
    with ChannelConsumer[F, Stream] {

  def basicQos(prefetchCount: Int): F[Unit] =
    channel.delay(_.basicQos(prefetchCount))

  def basicConsume(
      queue: QueueName,
      deliverCallback: client.DeliverCallback,
      cancelCallback: client.CancelCallback
  ): F[ConsumerTag] =
    channel.delay(_.basicConsume(queue.name, deliverCallback, cancelCallback)).map(ConsumerTag(_))

  def basicConsume(queue: QueueName, consumer: client.Consumer): F[ConsumerTag] =
    channel.delay(_.basicConsume(queue.name, consumer)).map(ConsumerTag(_))

  def basicGet(queue: QueueName, autoAck: Boolean): F[client.GetResponse] =
    channel.delay(_.basicGet(queue.name, autoAck))

  def deliveryStream(
      queueName: QueueName,
      prefetchCount: Int
  ): F[(ConsumerTag, Stream[F, client.Delivery])] =
    basicQos(prefetchCount)
      .productR(consumerProvider.provide(prefetchCount))
      .flatMap { case (consumer, stream) => basicConsume(queueName, consumer).tupleRight(stream) }

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit] =
    channel.delay(_.basicAck(deliveryTag.number, multiple))

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    channel.delay(_.basicNack(deliveryTag.number, multiple, requeue))

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit] =
    channel.delay(_.basicReject(deliveryTag.number, requeue))

  def basicCancel(consumerTag: ConsumerTag): F[Unit] =
    channel.delay(_.basicCancel(consumerTag.name))

  def basicConfirm(outcome: Confirmation): F[Unit] =
    outcome match {
      case Ack(tag, multiple) => basicAck(tag, multiple)
      case Nack(tag, multiple, requeue) => basicNack(tag, multiple, requeue)
      case Reject(tag, requeue) => basicReject(tag, requeue)
    }

  def queueDeclare(queueDeclaration: QueueDeclaration): F[Queue.DeclareOk] =
    channel.delay {
      _.queueDeclare(
        queueDeclaration.queueName.name,
        queueDeclaration.durable.bool,
        queueDeclaration.exclusive.bool,
        queueDeclaration.autoDelete.bool,
        queueDeclaration.arguments.asJava
      )
    }

  def exchangeDeclare(exchangeDeclaration: ExchangeDeclaration): F[Exchange.DeclareOk] =
    channel.delay {
      _.exchangeDeclare(
        exchangeDeclaration.exchangeName.name,
        exchangeDeclaration.exchangeType,
        exchangeDeclaration.durable.bool,
        exchangeDeclaration.autoDelete.bool,
        exchangeDeclaration.internal.bool,
        exchangeDeclaration.arguments.asJava
      )
    }

  def queueBind(bindDeclaration: BindDeclaration): F[Queue.BindOk] =
    channel.delay {
      _.queueBind(
        bindDeclaration.queueName.name,
        bindDeclaration.exchangeName.name,
        bindDeclaration.routingKey.name,
        bindDeclaration.arguments.asJava
      )
    }

  def declare(declaration: Declaration): F[Method] =
    declaration match {
      case queue: QueueDeclaration       => queueDeclare(queue).widen[Method]
      case exchange: ExchangeDeclaration => exchangeDeclare(exchange).widen[Method]
      case bind: BindDeclaration         => queueBind(bind).widen[Method]
    }

  def queueUnbind(bind: BindDeclaration): F[Queue.UnbindOk] =
    channel.delay(_.queueUnbind(bind.queueName.name, bind.exchangeName.name, bind.routingKey.name))

  def queueDelete(queueName: QueueName): F[Queue.DeleteOk] =
    channel.delay(_.queueDelete(queueName.name))

  def exchangeDelete(exchangeName: ExchangeName): F[Exchange.DeleteOk] =
    channel.delay(_.exchangeDelete(exchangeName.name))

  def basicPublishDirect[V](
      queueName: QueueName,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties()
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    basicPublish(ExchangeName.default, RoutingKey(queueName.name), body, mandatory, properties)

  def basicPublishFanout[V](
      exchangeName: ExchangeName,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties()
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    basicPublish(exchangeName, RoutingKey.default, body, mandatory, properties)

  def basicPublish[V](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      body: V,
      mandatory: MandatoryArgument = MandatoryArgument.NonMandatory,
      properties: BasicProperties = new BasicProperties()
  )(implicit encoder: BodyEncoder[V]): F[Unit] = {
    val props = encoder.alterProps(properties)
    channel.delay(
      _.basicPublish(
        exchangeName.name,
        routingKey.name,
        mandatory.bool,
        props,
        encoder.encode(body)
      )
    )
  }

  def delay[V](f: client.Channel => V): F[V] =
    channel.delay(f)

  def isOpen: F[Boolean] =
    channel.isOpen
}
