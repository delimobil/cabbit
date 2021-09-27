package ru.delimobil.cabbit.model

final case class QueueName(name: String) extends AnyVal

object QueueName {
  // Auto assigned name
  val default: QueueName = QueueName("")
}
