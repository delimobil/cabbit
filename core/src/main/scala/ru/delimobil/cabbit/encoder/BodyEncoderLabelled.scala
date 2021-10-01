package ru.delimobil.cabbit.encoder

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.model.ContentEncoding
import ru.delimobil.cabbit.model.ContentType

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
