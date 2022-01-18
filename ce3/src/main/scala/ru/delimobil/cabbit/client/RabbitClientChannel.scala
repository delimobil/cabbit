package ru.delimobil.cabbit.client

import cats.FlatMap
import fs2.Stream
import ru.delimobil.cabbit.api.Channel
import ru.delimobil.cabbit.core.ChannelBlocking

private[client] final class RabbitClientChannel[F[_]: FlatMap](
    channel: ChannelBlocking[F],
    consumerProvider: RabbitClientConsumerProvider[F, Stream[F, *]]
) extends RabbitClientChannelImpl[F, Stream[F, *]](channel, consumerProvider)
    with Channel[F]
