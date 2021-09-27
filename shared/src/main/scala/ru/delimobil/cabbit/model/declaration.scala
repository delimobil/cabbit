package ru.delimobil.cabbit.model

import com.rabbitmq.client.BuiltinExchangeType

object declaration {

  final case class DurableConfig(bool: Boolean) extends AnyVal

  object DurableConfig {
    val Durable: DurableConfig = DurableConfig(true)
    val NonDurable: DurableConfig = DurableConfig(false)
  }

  final case class ExclusiveConfig(bool: Boolean) extends AnyVal

  object ExclusiveConfig {
    val Exclusive: ExclusiveConfig = ExclusiveConfig(true)
    val NonExclusive: ExclusiveConfig = ExclusiveConfig(false)
  }

  final case class AutoDeleteConfig(bool: Boolean) extends AnyVal

  object AutoDeleteConfig {
    val AutoDelete: AutoDeleteConfig =  AutoDeleteConfig(true)
    val NonAutoDelete: AutoDeleteConfig = AutoDeleteConfig(false)
  }

  final case class InternalConfig(bool: Boolean) extends AnyVal

  object InternalConfig {
    val Internal: InternalConfig = InternalConfig(true)
    val NonInternal: InternalConfig = InternalConfig(false)
  }

  type Arguments = Map[String, Any]

  sealed trait Declaration

  case class QueueDeclaration(
    queueName: QueueName,
    durable: DurableConfig = DurableConfig.NonDurable,
    exclusive: ExclusiveConfig = ExclusiveConfig.Exclusive,
    autoDelete: AutoDeleteConfig = AutoDeleteConfig.AutoDelete,
    arguments: Arguments = Map.empty,
  ) extends Declaration

  case class ExchangeDeclaration(
    exchangeName: ExchangeName,
    exchangeType: BuiltinExchangeType,
    durable: DurableConfig = DurableConfig.NonDurable,
    autoDelete: AutoDeleteConfig = AutoDeleteConfig.NonAutoDelete,
    internal: InternalConfig = InternalConfig.NonInternal,
    arguments: Arguments = Map.empty,
  ) extends Declaration

  // AMQP-0-9-1 The server MUST, in each virtual host, pre-declare at least two direct exchange instances: one
  // named "amq.direct", the other with no public name that serves as a default exchange for Publish methods.
  //
  // Thus, these Defaults are created automatically for every virtual host,
  // mentioned here for the purpose of documentation.
  private[cabbit] object Defaults {
    // The server MUST pre-declare a direct exchange with no public name to act as the default
    // exchange for content Publish methods and for default queue bindings
    val ExchangeDeclarationDefault: ExchangeDeclaration =
      ExchangeDeclaration(ExchangeName.default, BuiltinExchangeType.DIRECT)

    def bindDeclarationDefault(queueName: QueueName): BindDeclaration =
      BindDeclaration(queueName, ExchangeName.default, RoutingKey(queueName.name))
  }

  case class BindDeclaration(
    queueName: QueueName,
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    arguments: Arguments = Map.empty,
  ) extends Declaration
}
