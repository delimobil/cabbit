package ru.delimobil.cabbit.model

sealed trait Confirmation

object Confirmation {

  case class Ack(tag: DeliveryTag, multiple: Boolean) extends Confirmation

  case class Nack(tag: DeliveryTag, multiple: Boolean, requeue: Boolean) extends Confirmation

  case class Reject(tag: DeliveryTag, requeue: Boolean) extends Confirmation
}
