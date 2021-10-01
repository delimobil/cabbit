package ru.delimobil.cabbit.encoder

import ru.delimobil.cabbit.model.ContentEncoding
import ru.delimobil.cabbit.model.ContentEncoding.GzippedEncoding
import ru.delimobil.cabbit.model.ContentEncoding.IdentityEncoding
import ru.delimobil.cabbit.model.ContentEncoding.encodeUtf8
import ru.delimobil.cabbit.model.ContentEncoding.gzip
import ru.delimobil.cabbit.model.ContentType
import ru.delimobil.cabbit.model.ContentType.TextContentType

object string {

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
}
