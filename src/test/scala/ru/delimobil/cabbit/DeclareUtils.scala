package ru.delimobil.cabbit

import java.util.UUID

import cats.effect.Sync
import com.rabbitmq.client.BuiltinExchangeType
import ru.delimobil.cabbit.algebra.ExchangeName
import ru.delimobil.cabbit.algebra.QueueName
import ru.delimobil.cabbit.config.declaration.Arguments
import ru.delimobil.cabbit.config.declaration.AutoDeleteConfig
import ru.delimobil.cabbit.config.declaration.DurableConfig
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.ExclusiveConfig
import ru.delimobil.cabbit.config.declaration.InternalConfig
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

final class DeclareUtils[F[_]: Sync] {

  def uuidIO: F[UUID] =
    Sync[F].delay(UUID.randomUUID())

  private def getExchange(uuid: UUID, tpe: BuiltinExchangeType, props: Arguments) =
    ExchangeDeclaration(
      ExchangeName(s"the-exchange-$uuid"),
      tpe,
      DurableConfig.NonDurable,
      AutoDeleteConfig.NonAutoDelete,
      InternalConfig.NonInternal,
      props,
    )

  def direct(uuid: UUID): ExchangeDeclaration =
    getExchange(uuid, BuiltinExchangeType.DIRECT, Map.empty)

  def topic(uuid: UUID, props: Arguments): ExchangeDeclaration =
    getExchange(uuid, BuiltinExchangeType.TOPIC, props)

  def fanout(uuid: UUID): ExchangeDeclaration =
    getExchange(uuid, BuiltinExchangeType.FANOUT, Map.empty)

  private def getQueue(uuid: UUID, exclusivity: ExclusiveConfig, props: Arguments): QueueDeclaration =
    QueueDeclaration(
      QueueName(s"the-queue-$uuid"),
      DurableConfig.NonDurable,
      exclusivity,
      AutoDeleteConfig.NonAutoDelete,
      props,
    )

  def nonExclusive(uuid: UUID, props: Arguments): QueueDeclaration =
    getQueue(uuid, ExclusiveConfig.NonExclusive, props)

  def exclusive(uuid: UUID): QueueDeclaration =
    getQueue(uuid, ExclusiveConfig.Exclusive, Map.empty)
}
