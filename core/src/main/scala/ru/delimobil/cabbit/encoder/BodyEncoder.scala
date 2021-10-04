package ru.delimobil.cabbit.encoder

import com.rabbitmq.client.AMQP.BasicProperties

trait BodyEncoder[V] {
  def encode(body: V): Array[Byte]
  def alterProps(props: BasicProperties): BasicProperties
}

object BodyEncoder {
  def apply[V](implicit encoder: BodyEncoder[V]): BodyEncoder[V] = encoder
}
