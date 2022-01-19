package ru.delimobil.cabbit

import ru.delimobil.cabbit.model.declaration.Arguments

import scala.collection.JavaConverters

private[cabbit] object CollectionConverters {

  implicit class argOps(val args: Arguments) extends AnyVal {
    def asJava: java.util.Map[String, Object] =
      JavaConverters.mapAsJavaMap(args.asInstanceOf[Map[String, AnyRef]])
  }

  implicit class listOps[V](val list: List[V]) extends AnyVal {
    def asJava: java.util.List[V] = JavaConverters.seqAsJavaList(list)
  }

  implicit class chainingOps[V](val v: V) extends AnyVal {
    def pipe[T](f: V => T): T = f(v)
  }
}
