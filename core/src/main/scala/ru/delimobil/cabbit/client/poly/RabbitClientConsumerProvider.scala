package ru.delimobil.cabbit.client.poly

import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Delivery

private[client] trait RabbitClientConsumerProvider[F[_], Stream[*[_], _]] {
  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])]
}
