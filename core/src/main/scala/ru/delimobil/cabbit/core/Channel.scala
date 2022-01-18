package ru.delimobil.cabbit.core

private[cabbit] trait Channel[F[_], S[_]]
    extends ChannelDeclaration[F]
    with ChannelPublisher[F]
    with ChannelAcker[F]
    with ChannelConsumer[F, S]
    with ChannelExtendable[F]
