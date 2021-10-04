package ru.delimobil.cabbit.encoder

import io.circe.Encoder
import io.circe.syntax._
import ru.delimobil.cabbit.model.ContentEncoding
import ru.delimobil.cabbit.model.ContentEncoding.GzippedEncoding
import ru.delimobil.cabbit.model.ContentEncoding.IdentityEncoding
import ru.delimobil.cabbit.model.ContentEncoding.encodeUtf8
import ru.delimobil.cabbit.model.ContentEncoding.gzip
import ru.delimobil.cabbit.model.ContentType
import ru.delimobil.cabbit.model.ContentType.JsonContentType

object json {

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
