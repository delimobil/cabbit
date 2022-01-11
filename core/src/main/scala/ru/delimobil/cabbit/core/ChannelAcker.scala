package ru.delimobil.cabbit.core

import ru.delimobil.cabbit.model.Confirmation
import ru.delimobil.cabbit.model.DeliveryTag

private[cabbit] trait ChannelAcker[F[_]] {

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit]

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit]

  def basicConfirm(outcome: Confirmation): F[Unit]
}
