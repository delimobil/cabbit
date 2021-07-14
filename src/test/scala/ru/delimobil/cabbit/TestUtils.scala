// package ru.delimobil.cabbit

// import cats.effect.{IO, Timer}
// import cats.instances.list._
// import cats.syntax.apply._
// import cats.syntax.traverse._
// import com.rabbitmq.client.AMQP.BasicProperties
// import com.rabbitmq.client.BuiltinExchangeType
// import ru.delimobil.cabbit.algebra.BodyEncoder.instances.jsonGzip
// import ru.delimobil.cabbit.algebra.{Connection, QueueName, _}
// import ru.delimobil.cabbit.config.declaration._

// import java.util.UUID
// import scala.concurrent.ExecutionContext
// import scala.concurrent.duration._
// import com.rabbitmq.client.Delivery

// object TestUtils {

//   def channelWithPublishedMessage(connection: Connection[IO])(messages: List[String])(
//     assertF: (ChannelDeclaration[IO], ChannelConsumer[IO], QueueDeclaration) => IO[Unit]
//   )(implicit t: Timer[IO]): Unit = {
//     val declaration = connection.createChannelDeclaration
//     val consumer = connection.createChannelConsumer
//     val publisher = connection.createChannelPublisher
//     val action =
//       (declaration, consumer, publisher)
//         .tupled
//         .use {
//           case (declaration, consumerChannel, publisherChannel) =>
//             for {
//               declarations <- TestUtils.declare(declaration)
//               (_, queue, bind) = declarations
//               _ <- messages.traverse { message =>
//                 publisherChannel.basicPublish(bind.exchangeName, bind.routingKey, new BasicProperties, mandatory = true, message)
//               }
//               _ <- IO.sleep(50.millis)
//               _ <- assertF(declaration, consumerChannel, queue)
//             } yield {}
//         }

//     action.unsafeRunSync()
//   }
// }
