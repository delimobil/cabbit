package ru.delimobil.cabbit.api

trait ShutdownNotifier[F[_]] {
  def isOpen: F[Boolean]
}
