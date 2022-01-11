package ru.delimobil.cabbit.core

import com.rabbitmq.client

private[cabbit] trait ChannelExtendable[F[_]] {
  def delay[V](f: client.Channel => V): F[V]
}
