package ru.delimobil.cabbit.teaser

import cats.data.NonEmptyList
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import ru.delimobil.cabbit.ConnectionFactoryProvider
import ru.delimobil.cabbit.model.CabbitConfig
import ru.delimobil.cabbit.model.CabbitConfig.CabbitNodeConfig
import ru.delimobil.cabbit.model.ExchangeName
import ru.delimobil.cabbit.model.RoutingKey

object TeaserPublishApp extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    ArgsValidator.validate(args) match {
      case Left(value)         => IO.delay(println(value)).as(ExitCode.Error)
      case Right((host, port)) => push(host, port).as(ExitCode.Success)
    }

  private def push(host: String, port: Int): IO[Unit] = {
    import ru.delimobil.cabbit.encoder.string.textUtf8

    val node = CabbitNodeConfig(host, port)
    val config = CabbitConfig(NonEmptyList.one(node), "/")
    val exchange = ExchangeName.default
    val rk = RoutingKey("teaser-example-queue")
    ConnectionFactoryProvider
      .provide[IO](config, sslContext = None)
      .newConnection(config.addresses)
      .flatMap(_.createChannelPublisher)
      .use(_.basicPublish(exchange, rk, "hello from cabbit") *> log("published"))
  }

  private def log(s: String): IO[Unit] = IO.println(s"publisher: $s")
}
