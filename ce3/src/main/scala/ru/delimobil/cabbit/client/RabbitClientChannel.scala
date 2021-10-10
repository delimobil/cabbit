package ru.delimobil.cabbit.client

import cats.FlatMap
import fs2.Stream
import ru.delimobil.cabbit.api.Channel
import ru.delimobil.cabbit.api.ChannelOnPool
import ru.delimobil.cabbit.client.poly.RabbitClientConsumerProvider

private[client] final class RabbitClientChannel[F[_]: FlatMap](
    channel: ChannelOnPool[F],
    consumerProvider: RabbitClientConsumerProvider[F, Stream]
) extends poly.RabbitClientChannel[F, Stream](channel, consumerProvider)
    with Channel[F]
