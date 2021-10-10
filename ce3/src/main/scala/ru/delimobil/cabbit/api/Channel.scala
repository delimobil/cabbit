package ru.delimobil.cabbit.api

trait Channel[F[_]] extends ChannelDeclaration[F] with ChannelPublisher[F] with ChannelConsumer[F]
