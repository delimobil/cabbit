package ru.delimobil.cabbit.algebra

final case class QueueName(name: String) extends AnyVal

final case class ExchangeName(name: String) extends AnyVal

final case class RoutingKey(name: String) extends AnyVal

final case class ConsumerTag(name: String) extends AnyVal

final case class DeliveryTag(number: Long) extends AnyVal

object defaults {
  // Auto assigned name
  val QueueNameDefault: QueueName = QueueName("")

  // AMQP 0-9-1 The server MUST pre-declare a direct exchange with no public name to
  // act as the default exchange for content Publish methods and for default queue bindings
  val ExchangeNameDefault: ExchangeName = ExchangeName("")

  val ExchangeNameTopic: ExchangeName = ExchangeName("amq.topic")

  // Is used for FANOUT exchanges
  val RoutingKeyDefault: RoutingKey = RoutingKey("")
}
