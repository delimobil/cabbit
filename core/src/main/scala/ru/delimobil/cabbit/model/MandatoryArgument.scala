package ru.delimobil.cabbit.model

sealed abstract class MandatoryArgument(val bool: Boolean)

object MandatoryArgument {

  object Mandatory extends MandatoryArgument(true)

  object NonMandatory extends MandatoryArgument(false)
}
