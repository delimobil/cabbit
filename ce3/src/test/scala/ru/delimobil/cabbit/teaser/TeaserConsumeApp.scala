package ru.delimobil.cabbit.teaser

import cats.data.NonEmptyList
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import fs2.Stream
import ru.delimobil.cabbit.ConnectionFactoryProvider
import ru.delimobil.cabbit.model.CabbitConfig
import ru.delimobil.cabbit.model.CabbitConfig.CabbitNodeConfig
import ru.delimobil.cabbit.model.CabbitConfig.Host
import ru.delimobil.cabbit.model.CabbitConfig.Port
import ru.delimobil.cabbit.model.ContentEncoding
import ru.delimobil.cabbit.model.QueueName
import ru.delimobil.cabbit.model.declaration.QueueDeclaration
import ru.delimobil.cabbit.syntax._

object TeaserConsumeApp extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    ArgsValidator.validate(args) match {
      case Left(value)         => IO.delay(println(value)).as(ExitCode.Error)
      case Right((host, port)) => consume(host, port).as(ExitCode.Success)
    }

  private def consume(host: Host, port: Port): IO[Unit] = {
    val node = CabbitNodeConfig(host, port)
    val config = CabbitConfig(NonEmptyList.one(node), "/")
    val queue = QueueName("teaser-example-queue")
    ConnectionFactoryProvider
      .provide[IO](config, sslContext = None)
      .newConnection(config.addresses)
      .flatMap(_.createChannel)
      .use { channel =>
        val consumer =
          Stream
            .force(channel.deliveryStream(queue, prefetchCount = 10).map(_._2) <* log("started"))
            .evalMap { d =>
              val body = ContentEncoding.decodeUtf8(d.getBody)
              channel.basicAck(d.deliveryTag, multiple = false).as(body)
            }
            .take(2) // remove to make it endless
            .foreach(log)
            .onComplete(Stream.exec(log("completed")))
            .compile
            .drain

        channel.queueDeclare(QueueDeclaration(queue)) *> consumer
      }
  }

  private def log(s: String): IO[Unit] = IO.delay(println(s"consumer: $s"))
}
