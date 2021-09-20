package ru.delimobil.cabbit.config

import cats.data.NonEmptyList
import cats.syntax.option._
import com.rabbitmq.client
import com.rabbitmq.client.DefaultSaslConfig
import com.rabbitmq.client.SaslConfig
import javax.net.ssl.SSLContext
import ru.delimobil.cabbit.config.CabbitConfig._

import scala.concurrent.duration.FiniteDuration

/** @param automaticRecovery <a href="https://www.rabbitmq.com/api-guide.html#recovery"> */
case class CabbitConfig(
  nodes: NonEmptyList[CabbitNodeConfig],
  virtualHost: String,
  connectionTimeout: FiniteDuration,
  username: Option[String],
  password: Option[String],
  automaticRecovery: Boolean = true
)

object CabbitConfig {

  type Host = String

  type Port = Int

  final case class CabbitNodeConfig(host: Host, port: Port)

  implicit class cabbitConfigOps(val config: CabbitConfig) extends AnyVal {

    def addresses: List[client.Address] =
      config.nodes.map(node => new client.Address(node.host, node.port)).toList

    def factory(ssl: Boolean, context: Option[SSLContext], saslConfig: SaslConfig): client.ConnectionFactory = {
      val factory = new client.ConnectionFactory()

      val firstNode = config.nodes.head
      factory.setHost(firstNode.host)
      factory.setPort(firstNode.port)
      factory.setVirtualHost(config.virtualHost)
      factory.setConnectionTimeout(config.connectionTimeout.toMillis.toInt)
      factory.setAutomaticRecoveryEnabled(config.automaticRecovery)
      if (ssl) context.fold(factory.useSslProtocol())(factory.useSslProtocol)
      factory.setSaslConfig(saslConfig)
      config.username.foreach(factory.setUsername)
      config.password.foreach(factory.setPassword)

      factory
    }

    def factoryDefaultSsl: client.ConnectionFactory =
      factory(ssl = false, none, DefaultSaslConfig.PLAIN)

    def factoryExternalSsl(context: SSLContext): client.ConnectionFactory =
      factory(ssl = true, context.some, DefaultSaslConfig.EXTERNAL)
  }
}
