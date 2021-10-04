package ru.delimobil.cabbit.algebra

import ru.delimobil.cabbit.model.DeliveryTag

trait ChannelAcker[F[_]] extends ShutdownNotifier[F] {

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit]

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit]
}
