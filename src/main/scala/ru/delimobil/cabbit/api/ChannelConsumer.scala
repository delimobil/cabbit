package ru.delimobil.cabbit.api

import fs2.Stream

trait ChannelConsumer[F[_]] extends poly.ChannelConsumer[F, Stream]
