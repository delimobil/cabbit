package ru.delimobil.cabbit.api

import ru.delimobil.cabbit.api.ChannelAcker.Confirmation
import ru.delimobil.cabbit.model.DeliveryTag

trait ChannelAcker[F[_]] extends ShutdownNotifier[F] {

  def basicAck(deliveryTag: DeliveryTag, multiple: Boolean): F[Unit]

  def basicNack(deliveryTag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]

  def basicReject(deliveryTag: DeliveryTag, requeue: Boolean): F[Unit]

  def basicConfirm(outcome: Confirmation): F[Unit]
}

object ChannelAcker {

  sealed trait Confirmation

  case class Ack(tag: DeliveryTag, multiple: Boolean) extends Confirmation

  case class Nack(tag: DeliveryTag, multiple: Boolean, requeue: Boolean) extends Confirmation

  case class Reject(tag: DeliveryTag, requeue: Boolean) extends Confirmation
}
