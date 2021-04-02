package ru.delimobil.cabbit.config

import cats.data.NonEmptyList
import com.rabbitmq.client.DefaultSaslConfig
import com.rabbitmq.client.SaslConfig
import ru.delimobil.cabbit.config.CabbitConfig._

/** @param automaticRecovery <a href="https://www.rabbitmq.com/api-guide.html#recovery"> */
case class CabbitConfig(
  nodes: NonEmptyList[CabbitNodeConfig],
  virtualHost: String,
  connectionTimeout: Int,
  username: Option[String],
  password: Option[String],
  sslConfig: SslConfig = SslConfig.default,
  automaticRecovery: Boolean = true,
)

object CabbitConfig {

  type Host = String

  type Port = Int

  case class CabbitNodeConfig(host: Host, port: Port)

  case class SslConfig(
    ssl: Boolean,
    specificProtocol: Option[String],
    saslConfig: SaslConfig,
  )

  object SslConfig {

    val default: SslConfig =
      SslConfig(
        ssl = false,
        specificProtocol = None,
        saslConfig = DefaultSaslConfig.PLAIN,
      )
  }
}
