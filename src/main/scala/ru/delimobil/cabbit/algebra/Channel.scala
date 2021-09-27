package ru.delimobil.cabbit.algebra

trait Channel[F[_]] extends ChannelDeclaration[F] with ChannelPublisher[F] with ChannelConsumer[F]
