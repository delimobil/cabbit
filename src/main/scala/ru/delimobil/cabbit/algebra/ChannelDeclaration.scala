package ru.delimobil.cabbit.algebra

import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.AMQP.Exchange
import ru.delimobil.cabbit.config.declaration.BindDeclaration
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

trait ChannelDeclaration[F[_]] extends ShutdownNotifier[F] {

  def queueDeclare(queueDeclaration: QueueDeclaration): F[Queue.DeclareOk]

  def exchangeDeclare(exchangeDeclaration: ExchangeDeclaration): F[Exchange.DeclareOk]

  def queueBind(queueBind: BindDeclaration): F[Queue.BindOk]

  def queueUnbind(bind: BindDeclaration): F[Queue.UnbindOk]

  def queueDelete(queueName: QueueName): F[Queue.DeleteOk]

  def exchangeDelete(exchangeName: ExchangeName): F[Exchange.DeleteOk]
}
