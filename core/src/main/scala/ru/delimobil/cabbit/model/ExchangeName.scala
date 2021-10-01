package ru.delimobil.cabbit.model

final case class ExchangeName(name: String) extends AnyVal

object ExchangeName {
  // AMQP 0-9-1 The server MUST pre-declare a direct exchange with no public name to
  // act as the default exchange for content Publish methods and for default queue bindings
  val default: ExchangeName = ExchangeName("")

  val topic: ExchangeName = ExchangeName("amq.topic")
}
