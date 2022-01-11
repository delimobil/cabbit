package ru.delimobil.cabbit.core

private[cabbit] trait ShutdownNotifier[F[_]] {
  def isOpen: F[Boolean]
}
