package ru.delimobil.cabbit.algebra

trait Channel[F[_]] extends ChannelDeclaration[F] with ChannelConsumer[F] with ChannelPublisher[F]
