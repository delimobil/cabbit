package ru.delimobil.cabbit.model

import com.rabbitmq.client.AMQP.BasicProperties
import io.circe.Encoder
import io.circe.syntax._
import ru.delimobil.cabbit.model.ContentEncoding._
import ru.delimobil.cabbit.model.ContentType._

trait BodyEncoder[V] {
  def encode(body: V): Array[Byte]
  def alterProps(props: BasicProperties): BasicProperties
}

object BodyEncoder {

  trait BodyEncoderLabelled[V] extends BodyEncoder[V] {

    def contentType: ContentType

    def contentEncoding: ContentEncoding

    final def alterProps(props: BasicProperties): BasicProperties =
      props
        .builder()
        .contentType(Option(props.getContentType).filter(_.nonEmpty).getOrElse(contentType.raw))
        .contentEncoding(
          Option(props.getContentEncoding).filter(_.nonEmpty).getOrElse(contentEncoding.raw)
        )
        .build()
  }

  object instances {

    implicit val plain: BodyEncoder[Array[Byte]] =
      new BodyEncoder[Array[Byte]] {
        def encode(body: Array[Byte]): Array[Byte] = body
        def alterProps(props: BasicProperties): BasicProperties = props
      }

    implicit val textUtf8: BodyEncoder[String] =
      new BodyEncoderLabelled[String] {
        def contentType: ContentType = TextContentType
        def contentEncoding: ContentEncoding = IdentityEncoding
        def encode(body: String): Array[Byte] = encodeUtf8(body)
      }

    implicit val textGzip: BodyEncoder[String] =
      new BodyEncoderLabelled[String] {
        def contentType: ContentType = TextContentType
        def contentEncoding: ContentEncoding = GzippedEncoding
        def encode(body: String): Array[Byte] = gzip(encodeUtf8(body))
      }

    implicit val protobuf: BodyEncoder[Array[Byte]] = protobufInstanceBy(identity)

    implicit def jsonUtf8[V: Encoder]: BodyEncoder[V] =
      new BodyEncoderLabelled[V] {
        def contentType: ContentType = JsonContentType
        def contentEncoding: ContentEncoding = IdentityEncoding
        def encode(body: V): Array[Byte] = encodeUtf8(body.asJson.noSpaces)
      }

    implicit def jsonGzip[V: Encoder]: BodyEncoder[V] =
      new BodyEncoderLabelled[V] {
        def contentType: ContentType = JsonContentType
        def contentEncoding: ContentEncoding = GzippedEncoding
        def encode(body: V): Array[Byte] = gzip(encodeUtf8(body.asJson.noSpaces))
      }
  }

  def protobufInstanceBy[V](f: V => Array[Byte]): BodyEncoder[V] =
    new BodyEncoderLabelled[V] {
      def contentType: ContentType = ProtobufContentType
      def contentEncoding: ContentEncoding = IdentityEncoding
      def encode(body: V): Array[Byte] = f(body)
    }

  def apply[V](implicit encoder: BodyEncoder[V]): BodyEncoder[V] = encoder
}
