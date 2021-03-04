package ru.delimobil.cabbit

package object algebra {

  case class QueueName(name: String) extends AnyVal

  case class ExchangeName(name: String) extends AnyVal

  case class RoutingKey(name: String) extends AnyVal

  case class ConsumerTag(name: String) extends AnyVal

  case class DeliveryTag(number: Long) extends AnyVal
}
