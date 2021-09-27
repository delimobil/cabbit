package ru.delimobil.cabbit.model

final case class RoutingKey(name: String) extends AnyVal

object RoutingKey {
  // Is used for FANOUT exchanges
  val default: RoutingKey = RoutingKey("")
}
