package ru.delimobil.cabbit

import java.util.UUID

import cats.effect.Sync
import com.rabbitmq.client.BuiltinExchangeType
import ru.delimobil.cabbit.algebra.ExchangeName
import ru.delimobil.cabbit.algebra.QueueName
import ru.delimobil.cabbit.config.declaration.Arguments
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

final class DeclareUtils[F[_]: Sync] {

  def uuidIO: F[UUID] =
    Sync[F].delay(UUID.randomUUID())

  private def getExchange(uuid: UUID, tpe: BuiltinExchangeType, props: Arguments) =
    ExchangeDeclaration(ExchangeName(s"the-exchange-$uuid"), tpe, arguments = props)

  def direct(uuid: UUID): ExchangeDeclaration =
    getExchange(uuid, BuiltinExchangeType.DIRECT, Map.empty)

  def topic(uuid: UUID, props: Arguments): ExchangeDeclaration =
    getExchange(uuid, BuiltinExchangeType.TOPIC, props)

  def fanout(uuid: UUID): ExchangeDeclaration =
    getExchange(uuid, BuiltinExchangeType.FANOUT, Map.empty)

  def getQueue(uuid: UUID, props: Arguments = Map.empty): QueueDeclaration =
    QueueDeclaration(QueueName(s"the-queue-$uuid"), arguments = props)
}
