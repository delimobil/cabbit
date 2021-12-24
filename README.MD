cabbit
=============

[![Latest version](https://img.shields.io/badge/cabbit-0.1.1-orange.svg)](https://github.com/delimobil/cabbit)

A [cabbit] is a fictional hybrid between a cat and a rabbit.

The main goal of the library is to wrap rabbit-client with cats-effect & fs2.
The wrapper is intended to be thin and resemble the client.
`fs2` & `amqp-client` are the only dependencies.

[cabbit]: https://en.wikipedia.org/wiki/Cabbit

## Usage

cabbit is cross built for `scala "2.12.15" | "2.13.6" | "3.0.2"` & `ce2 | ce3`.
Thus, you can choose from these artifacts:

|               | `ce 3.2.9`(through `fs2 3.1.3`) | `ce 2.5.1` (through `fs2 2.5.9`) |
|      ---      | --- | --- |
| scala 2.12.15 | cabbit_2.12 | cabbit_ce2_2.12 |
| scala 2.13.6  | cabbit_2.13 | cabbit_ce2_2.13 |
| scala 3.0.2   | cabbit_3    | cabbit_ce2_3    |


For ce3 versions add this instruction to your build.sbt:
```sbt
libraryDependencies += "ru.delimobil" %% "cabbit" % "0.1.1"
```
For ce2 versions replace `cabbit` with `cabbit_ce2`.
To use circe encoders add this artifact to your build.sbt:
```sbt
libraryDependencies += "ru.delimobil" %% "cabbit-circe" % "0.1.1"
```

## Teaser
This is how you can publish an arbitrary class to RabbitMQ:

```scala
    import io.circe.generic.auto._
    import ru.delimobil.cabbit.encoder.json.jsonUtf8

    case class Message(from: String, msg: String)    
    
    val node = CabbitNodeConfig(host, port)
    val config = CabbitConfig(NonEmptyList.one(node), "/")
    val exchange = ExchangeName.default
    val rk = RoutingKey("teaser-example-queue")
    val msg = Message("TeaserPublishApp", "hello from cabbit")
    ConnectionFactoryProvider
      .provide[IO](config, sslContext = None)
      .newConnection(config.addresses)
      .flatMap(_.createChannelPublisher)
      .use(_.basicPublish(exchange, rk, msg))
```

### Example
Ready to start? [Follow] the white cabbit.

[Follow]: https://github.com/delimobil/cabbit-example

### LICENSE ###
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
