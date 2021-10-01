package ru.delimobil.cabbit.algebra

import com.rabbitmq.client

trait ChannelOnPool[F[_]] extends ShutdownNotifier[F] {

  def delay[V](f: client.Channel => V): F[V]

  def close: F[Unit]
}
