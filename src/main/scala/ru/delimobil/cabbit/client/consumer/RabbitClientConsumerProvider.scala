package ru.delimobil.cabbit.client.consumer

import cats.effect.ConcurrentEffect
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Delivery
import fs2.Stream

private[client] trait RabbitClientConsumerProvider[F[_]] {
  def provide(prefetchCount: Int): F[(Consumer, Stream[F, Delivery])]
}

private[client] object RabbitClientConsumerProvider {

  def instance[F[_]: ConcurrentEffect]: RabbitClientConsumerProvider[F] = new QueueNoneTerminatedConsumerProvider[F]
}
