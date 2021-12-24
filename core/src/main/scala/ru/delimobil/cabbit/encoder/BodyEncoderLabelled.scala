package ru.delimobil.cabbit.encoder

import com.rabbitmq.client.AMQP.BasicProperties
import ru.delimobil.cabbit.model.ContentEncoding
import ru.delimobil.cabbit.model.ContentType

trait BodyEncoderLabelled[V] extends BodyEncoder[V] {

  def contentType: ContentType

  def contentEncoding: ContentEncoding

  final def alterProps(props: BasicProperties): BasicProperties = {
    val cType = Option(props.getContentType).filter(_.nonEmpty).getOrElse(contentType.raw)
    val propsEncoding = Option(props.getContentEncoding).filter(_.nonEmpty)
    val cEncoding = propsEncoding.getOrElse(contentEncoding.raw)
    props.builder().contentType(cType).contentEncoding(cEncoding).build()
  }
}
