package ru.delimobil.cabbit.config

import com.rabbitmq.client.BuiltinExchangeType
import ru.delimobil.cabbit.algebra._

object declaration {

  sealed abstract class DurableConfig(val bool: Boolean)

  object DurableConfig {
    object Durable extends DurableConfig(true)
    object NonDurable extends DurableConfig(false)
  }

  sealed abstract class ExclusiveConfig(val bool: Boolean)

  object ExclusiveConfig {
    object Exclusive extends ExclusiveConfig(true)
    object NonExclusive extends ExclusiveConfig(false)
  }

  sealed abstract class AutoDeleteConfig(val bool: Boolean)

  object AutoDeleteConfig {
    object AutoDelete extends AutoDeleteConfig(true)
    object NonAutoDelete extends AutoDeleteConfig(false)
  }

  sealed abstract class InternalConfig(val bool: Boolean)

  object InternalConfig {
    object Internal extends InternalConfig(true)
    object NonInternal extends InternalConfig(false)
  }

  type Arguments = Map[String, Any]

  case class QueueDeclaration(
    queueName: QueueName,
    durable: DurableConfig = DurableConfig.NonDurable,
    exclusive: ExclusiveConfig = ExclusiveConfig.Exclusive,
    autoDelete: AutoDeleteConfig = AutoDeleteConfig.AutoDelete,
    arguments: Arguments = Map.empty,
  )

  case class ExchangeDeclaration(
    exchangeName: ExchangeName,
    exchangeType: BuiltinExchangeType,
    durable: DurableConfig = DurableConfig.NonDurable,
    autoDelete: AutoDeleteConfig = AutoDeleteConfig.NonAutoDelete,
    internal: InternalConfig = InternalConfig.NonInternal,
    arguments: Arguments = Map.empty,
  )

  // AMQP-0-9-1 The server MUST, in each virtual host, pre-declare at least two direct exchange instances: one
  // named "amq.direct", the other with no public name that serves as a default exchange for Publish methods.
  //
  // Thus, these Defaults are created automatically for every virtual host,
  // mentioned here for the purpose of documentation.
  private[cabbit] object Defaults {

    // The server MUST pre-declare a direct exchange with no public name to act as the default
    // exchange for content Publish methods and for default queue bindings
    val ExchangeDeclarationDefault: ExchangeDeclaration =
      ExchangeDeclaration(ExchangeNameDefault, BuiltinExchangeType.DIRECT)

    def bindDeclarationDefault(queueName: QueueName): BindDeclaration =
      BindDeclaration(queueName, ExchangeNameDefault, RoutingKey(queueName.name))
  }

  case class BindDeclaration(
    queueName: QueueName,
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    arguments: Arguments = Map.empty,
  )
}
