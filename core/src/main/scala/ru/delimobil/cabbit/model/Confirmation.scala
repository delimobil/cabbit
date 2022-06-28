package ru.delimobil.cabbit.model

sealed trait Confirmation

object Confirmation {

  case class Ack(tag: DeliveryTag, multiple: Boolean) extends Confirmation

  case class Nack(tag: DeliveryTag, multiple: Boolean, requeue: Boolean) extends Confirmation

  case class Reject(tag: DeliveryTag, requeue: Boolean) extends Confirmation

  def ack(tag: DeliveryTag, multiple: Boolean): Confirmation =
    Ack(tag, multiple)

  def nack(tag: DeliveryTag, multiple: Boolean, requeue: Boolean): Confirmation =
    Nack(tag, multiple, requeue)

  def reject(tag: DeliveryTag, requeue: Boolean): Confirmation =
    Reject(tag, requeue)
}
