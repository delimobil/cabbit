package ru.delimobil.cabbit.algebra

import com.rabbitmq.client
import ru.delimobil.cabbit.config.declaration.BindDeclaration
import ru.delimobil.cabbit.config.declaration.ExchangeDeclaration
import ru.delimobil.cabbit.config.declaration.QueueDeclaration

trait ChannelDeclaration[F[_]] {

  def queueDeclare(queueDeclaration: QueueDeclaration): F[client.AMQP.Queue.DeclareOk]

  def exchangeDeclare(exchangeDeclaration: ExchangeDeclaration): F[client.AMQP.Exchange.DeclareOk]

  def queueBind(queueBind: BindDeclaration): F[Unit]

  def queueUnbind(bind: BindDeclaration): F[client.AMQP.Queue.UnbindOk]

  def queueDelete(queueName: QueueName): F[client.AMQP.Queue.DeleteOk]

  def exchangeDelete(exchangeName: ExchangeName): F[Unit]
}
