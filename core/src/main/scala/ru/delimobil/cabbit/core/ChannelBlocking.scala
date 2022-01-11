package ru.delimobil.cabbit.core

import com.rabbitmq.client

private[cabbit] trait ChannelBlocking[F[_]] {

  def delay[V](f: client.Channel => V): F[V]

  def close: F[Unit]
}
