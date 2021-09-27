package ru.delimobil.cabbit.algebra

import fs2.Stream

trait ChannelConsumer[F[_]] extends poly.ChannelConsumer[F, Stream]
