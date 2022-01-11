package ru.delimobil.cabbit.client

import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Delivery

private[client] trait RabbitClientConsumerProvider[F[_], S[_]] {
  def provide(prefetchCount: Int): F[(Consumer, S[Delivery])]
}
