package ru.delimobil.cabbit.algebra

import com.rabbitmq.client

private[cabbit] trait ChannelOnPool[F[_]] extends ShutdownNotifier[F] {

  def delay[V](f: client.Channel => V): F[V]

  def blockOn[V](f: client.Channel => F[V]): F[V]
}
