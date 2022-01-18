package ru.delimobil.cabbit.core

import com.rabbitmq.client

/* Descendants can be used to make extension methods */
private[cabbit] trait ConnectionExtendable[F[_]] {
  def delay[V](f: client.Connection => V): F[V]
}
