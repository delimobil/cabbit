package ru.delimobil.cabbit.encoder

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.model.ContentEncoding
import ru.delimobil.cabbit.model.ContentEncoding.IdentityEncoding
import ru.delimobil.cabbit.model.ContentType
import ru.delimobil.cabbit.model.ContentType.ProtobufContentType

object bytes {

  implicit val plain: BodyEncoder[Array[Byte]] =
    new BodyEncoder[Array[Byte]] {
      def encode(body: Array[Byte]): Array[Byte] = body
      def alterProps(props: BasicProperties): BasicProperties = props
    }

  implicit val protobuf: BodyEncoder[Array[Byte]] = protobufInstanceBy(identity)

  def protobufInstanceBy[V](f: V => Array[Byte]): BodyEncoder[V] =
    new BodyEncoderLabelled[V] {
      def contentType: ContentType = ProtobufContentType
      def contentEncoding: ContentEncoding = IdentityEncoding
      def encode(body: V): Array[Byte] = f(body)
    }
}
