package ru.delimobil.cabbit.client

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import com.rabbitmq.client
import ru.delimobil.cabbit.algebra.Connection
import ru.delimobil.cabbit.algebra.ConnectionFactory
import ru.delimobil.cabbit.client.consumer.QueueNoneTerminatedConsumerProvider
import ru.delimobil.cabbit.client.consumer.RabbitClientConsumerProvider

import scala.jdk.CollectionConverters._

private[cabbit] final class RabbitClientConnectionFactory[F[_]: ConcurrentEffect: ContextShift](
  blocker: Blocker,
  factory: => client.ConnectionFactory // '=>' factory is not thread safe
) extends ConnectionFactory[F] {

  private val consumerProvider: RabbitClientConsumerProvider[F] = new QueueNoneTerminatedConsumerProvider[F]

  def newConnection(addresses: List[client.Address], appName: Option[String] = None): Resource[F, Connection[F]] =
    createConnection(addresses, blocker, appName)
      .map(connection => new RabbitClientConnection[F](connection, blocker, consumerProvider))

  private def createConnection(
    addresses: List[client.Address],
    blocker: Blocker,
    appName: Option[String]
  ): Resource[F, client.Connection] = {
    val acquire = blocker.delay(factory.newConnection(addresses.asJava, appName.orNull))
    Resource.make(acquire)(connection => blocker.delay(close(connection)))
  }

  private def close(connection: client.Connection): Unit =
    try { connection.close() }
    catch { case _: client.AlreadyClosedException => () }
}
