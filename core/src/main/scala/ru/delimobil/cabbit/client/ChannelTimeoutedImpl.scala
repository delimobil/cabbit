package ru.delimobil.cabbit.client

import com.rabbitmq.client
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.AMQP.Exchange
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.GetResponse
import com.rabbitmq.client.Method
import ru.delimobil.cabbit.ce.api.Timeouter
import ru.delimobil.cabbit.core.Channel
import ru.delimobil.cabbit.encoder.BodyEncoder
import ru.delimobil.cabbit.model._
import ru.delimobil.cabbit.model.declaration._

import scala.concurrent.duration.FiniteDuration

private[cabbit] class ChannelTimeoutedImpl[F[_]: Timeouter, S[_]](
    delegatee: Channel[F, S],
    duration: FiniteDuration
) extends Channel[F, S] {

  final def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit] =
    timeout(delegatee.basicAck(deliveryTag, multiple))

  final def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    timeout(delegatee.basicNack(deliveryTag, multiple, requeue))

  final def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit] =
    timeout(delegatee.basicReject(deliveryTag, requeue))

  final def basicConfirm(outcome: Confirmation): F[Unit] =
    timeout(delegatee.basicConfirm(outcome))

  final def delay[V](f: client.Channel => V): F[V] =
    timeout(delegatee.delay(f))

  final def queueDeclare(queueDeclaration: QueueDeclaration): F[Queue.DeclareOk] =
    timeout(delegatee.queueDeclare(queueDeclaration))

  final def exchangeDeclare(exchangeDeclaration: ExchangeDeclaration): F[Exchange.DeclareOk] =
    timeout(delegatee.exchangeDeclare(exchangeDeclaration))

  final def queueBind(queueBind: BindDeclaration): F[Queue.BindOk] =
    timeout(delegatee.queueBind(queueBind))

  final def declare(declaration: Declaration): F[Method] =
    timeout(delegatee.declare(declaration))

  final def queueUnbind(bind: BindDeclaration): F[Queue.UnbindOk] =
    timeout(delegatee.queueUnbind(bind))

  final def queueDelete(queueName: QueueName): F[Queue.DeleteOk] =
    timeout(delegatee.queueDelete(queueName))

  final def exchangeDelete(exchangeName: ExchangeName): F[Exchange.DeleteOk] =
    timeout(delegatee.exchangeDelete(exchangeName))

  final def basicQos(prefetchCount: Int): F[Unit] =
    timeout(delegatee.basicQos(prefetchCount))

  final def basicConsume(
      queue: QueueName,
      deliverCallback: DeliverCallback,
      cancelCallback: CancelCallback
  ): F[ConsumerTag] =
    timeout(delegatee.basicConsume(queue, deliverCallback, cancelCallback))

  final def basicConsume(queue: QueueName, callback: Consumer): F[ConsumerTag] =
    timeout(delegatee.basicConsume(queue, callback))

  final def basicGet(queue: QueueName, autoAck: Boolean): F[GetResponse] =
    timeout(delegatee.basicGet(queue, autoAck))

  /* Pull of delivery stream is intentionally not timeouted, because
     time of message arrival is undefined.
   * */
  final def deliveryStream(queue: QueueName, prefetchCount: Int): F[(ConsumerTag, S[Delivery])] =
    timeout(delegatee.deliveryStream(queue, prefetchCount))

  final def basicCancel(consumerTag: ConsumerTag): F[Unit] =
    timeout(delegatee.basicCancel(consumerTag))

  final def basicPublishDirect[V](
      queueName: QueueName,
      body: V,
      mandatory: MandatoryArgument,
      properties: AMQP.BasicProperties
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    timeout(delegatee.basicPublishDirect(queueName, body, mandatory, properties))

  final def basicPublishFanout[V](
      exchangeName: ExchangeName,
      body: V,
      mandatory: MandatoryArgument,
      properties: AMQP.BasicProperties
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    timeout(delegatee.basicPublishFanout(exchangeName, body, mandatory, properties))

  final def basicPublish[V](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      body: V,
      mandatory: MandatoryArgument,
      properties: AMQP.BasicProperties
  )(implicit encoder: BodyEncoder[V]): F[Unit] =
    timeout(delegatee.basicPublish(exchangeName, routingKey, body, mandatory, properties))

  private def timeout[V](action: F[V]): F[V] =
    implicitly[Timeouter[F]].timeout(action, duration)
}
