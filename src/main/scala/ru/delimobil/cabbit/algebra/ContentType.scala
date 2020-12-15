package ru.delimobil.cabbit.algebra

case class ContentType(raw: String) extends AnyVal

object ContentType {

  val JsonContentType: ContentType = ContentType("application/json")

  val TextContentType: ContentType = ContentType("text/plain")
}
