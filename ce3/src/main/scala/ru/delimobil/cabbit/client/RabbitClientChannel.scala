package ru.delimobil.cabbit.client

import cats.FlatMap
import ru.delimobil.cabbit.api.Channel
import ru.delimobil.cabbit.core.ChannelBlocking

private[client] final class RabbitClientChannel[F[_]: FlatMap](
    channel: ChannelBlocking[F],
    consumerProvider: RabbitClientConsumerProvider[F, fs2.Stream[F, *]]
) extends RabbitClientChannelImpl[F, fs2.Stream[F, *]](channel, consumerProvider)
    with Channel[F]
