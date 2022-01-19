package ru.delimobil.cabbit.client

import fs2.Stream
import ru.delimobil.cabbit.api.Channel
import ru.delimobil.cabbit.ce.api.Timeouter

import scala.concurrent.duration.FiniteDuration

final class ChannelTimeouted[F[_]: Timeouter](
    delegatee: Channel[F],
    duration: FiniteDuration
) extends ChannelTimeoutedImpl[F, Stream[F, *]](delegatee, duration)
    with Channel[F]
