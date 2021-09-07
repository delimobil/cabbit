package ru.delimobil.cabbit.client.consumer

import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Delivery
import fs2.Stream

private[client] trait RabbitClientConsumerProvider[F[_]] {
  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])]
}
