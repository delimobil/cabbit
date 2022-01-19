package ru.delimobil.cabbit.core

import com.rabbitmq.client

/* Descendants can be used to make extension methods */
private[cabbit] trait ChannelExtendable[F[_]] {
  def delay[V](f: client.Channel => V): F[V]
}
