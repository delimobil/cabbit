package ru.delimobil.cabbit.algebra

import io.circe.Encoder
import io.circe.syntax._
import ru.delimobil.cabbit.algebra.ContentEncoding._
import ru.delimobil.cabbit.algebra.ContentType._

trait BodyEncoder[V] {
  def contentType: ContentType
  def contentEncoding: ContentEncoding
  def encode(body: V): Array[Byte]
}

object BodyEncoder {

  object instances {

    implicit val textUtf8: BodyEncoder[String] =
      new BodyEncoder[String] {
        def contentType: ContentType = TextContentType
        def contentEncoding: ContentEncoding = IdentityEncoding
        def encode(body: String): Array[Byte] = encodeUtf8(body)
      }

    implicit val textGzip: BodyEncoder[String] =
      new BodyEncoder[String] {
        def contentType: ContentType = TextContentType
        def contentEncoding: ContentEncoding = GzippedEncoding
        def encode(body: String): Array[Byte] = gzip(encodeUtf8(body))
      }

    implicit val protobuf: BodyEncoder[Array[Byte]] = protobufInstanceBy(identity)

    implicit def jsonUtf8[V: Encoder]: BodyEncoder[V] =
      new BodyEncoder[V] {
        def contentType: ContentType = JsonContentType
        def contentEncoding: ContentEncoding = IdentityEncoding
        def encode(body: V): Array[Byte] = encodeUtf8(body.asJson.noSpaces)
      }

    implicit def jsonGzip[V: Encoder]: BodyEncoder[V] =
      new BodyEncoder[V] {
        def contentType: ContentType = JsonContentType
        def contentEncoding: ContentEncoding = GzippedEncoding
        def encode(body: V): Array[Byte] = gzip(encodeUtf8(body.asJson.noSpaces))
      }
  }

  def protobufInstanceBy[V](f: V => Array[Byte]) =
    new BodyEncoder[V] {
      def contentType: ContentType = ProtobufContentType
      def contentEncoding: ContentEncoding = IdentityEncoding
      def encode(body: V): Array[Byte] = f(body)
    }

  def apply[V](implicit encoder: BodyEncoder[V]): BodyEncoder[V] = encoder
}
