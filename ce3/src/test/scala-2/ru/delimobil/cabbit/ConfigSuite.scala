package ru.delimobil.cabbit

import cats.data.NonEmptyList
import cats.syntax.either._
import com.rabbitmq.client.BuiltinExchangeType
import com.typesafe.config.ConfigFactory.parseString
import com.typesafe.config.ConfigValue
import org.scalatest.funsuite.AnyFunSuite
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.cats._
import ru.delimobil.cabbit.model.CabbitConfig.CabbitNodeConfig
import ru.delimobil.cabbit.model.CabbitConfig
import ru.delimobil.cabbit.model.declaration._
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.QueueName

import scala.concurrent.duration._

class ConfigSuite extends AnyFunSuite {

  implicit val reader: ConfigReader[Map[String, Any]] =
    ConfigReader[Map[String, ConfigValue]].map(_.map { case (s, value) => s -> value.unwrapped() })

  test("CabbitConfig can be extracted by pureconfig") {
    val conf = parseString("""nodes = [
                             |  {
                             |    host: "localhost",
                             |    port: 5672
                             |  }
                             |]
                             |virtual-host = "/"
                             |connection-timeout = 60.seconds
                             |""".stripMargin)

    val expected =
      CabbitConfig(
        nodes = NonEmptyList(CabbitNodeConfig("localhost", 5672), List.empty),
        virtualHost = "/",
        connectionTimeout = 60.seconds,
        username = None,
        password = None,
        automaticRecovery = true
      )

    assert(expected.asRight == ConfigSource.fromConfig(conf).load[CabbitConfig])
  }

  test("CabbitConfig with parameters can be extracted by pureconfig") {
    val conf = parseString("""nodes = [
                             |  {
                             |    host: "localhost",
                             |    port: 5672
                             |  }
                             |]
                             |virtual-host = "/"
                             |connection-timeout = 60.seconds
                             |username = "John"
                             |password = "Snow"
                             |automatic-recovery = true
                             |""".stripMargin)

    val expected =
      CabbitConfig(
        nodes = NonEmptyList(CabbitNodeConfig("localhost", 5672), List.empty),
        virtualHost = "/",
        connectionTimeout = 60.seconds,
        username = Some("John"),
        password = Some("Snow"),
        automaticRecovery = true
      )

    assert(expected.asRight == ConfigSource.fromConfig(conf).load[CabbitConfig])
  }

  test("QueueDeclaration can be extracted by pureconfig") {
    val conf = parseString("""queue-name = "queueName"""")
    val expected = QueueDeclaration(queueName = QueueName("queueName"))
    assert(expected.asRight == ConfigSource.fromConfig(conf).load[QueueDeclaration])
  }

  test("QueueDeclaration with arguments can be extracted by pureconfig") {
    val conf = parseString("""
                             |queue-name = "queueName"
                             |durable = true
                             |exclusive = false
                             |auto-delete = false
                             |arguments = {
                             |  string-param = "string"
                             |  int-param = 10
                             |  double-param = 0.1
                             |}
                             |""".stripMargin)

    val expected =
      QueueDeclaration(
        queueName = QueueName("queueName"),
        durable = DurableConfig.Durable,
        exclusive = ExclusiveConfig.NonExclusive,
        autoDelete = AutoDeleteConfig.NonAutoDelete,
        arguments = Map("string-param" -> "string", "int-param" -> 10, "double-param" -> 0.1)
      )

    assert(expected.asRight == ConfigSource.fromConfig(conf).load[QueueDeclaration])
  }

  test("ExchangeDeclaration can be extracted by pureconfig") {
    val conf = parseString("""
                             |exchange-name = "exchangeName"
                             |exchange-type = "DIRECT"
                             |""".stripMargin)

    val expected = ExchangeDeclaration(ExchangeName("exchangeName"), BuiltinExchangeType.DIRECT)

    assert(expected.asRight == ConfigSource.fromConfig(conf).load[ExchangeDeclaration])
  }

  test("ExchangeDeclaration with argumetns can be extracted by pureconfig") {
    val conf = parseString("""
                             |exchange-name = "exchangeName"
                             |exchange-type = "FANOUT"
                             |durable = true
                             |auto-delete = true
                             |internal = true
                             |arguments = {
                             |  string-param = "string"
                             |  int-param = 10
                             |  double-param = 0.1
                             |}
                             |""".stripMargin)

    val expected =
      ExchangeDeclaration(
        exchangeName = ExchangeName("exchangeName"),
        exchangeType = BuiltinExchangeType.FANOUT,
        durable = DurableConfig.Durable,
        autoDelete = AutoDeleteConfig.AutoDelete,
        internal = InternalConfig.Internal,
        arguments = Map("string-param" -> "string", "int-param" -> 10, "double-param" -> 0.1)
      )

    assert(expected.asRight == ConfigSource.fromConfig(conf).load[ExchangeDeclaration])
  }
}
