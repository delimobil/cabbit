package ru.delimobil.cabbit.algebra

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException
import cats.syntax.either._

case class ContentEncoding(raw: String) extends AnyVal

object ContentEncoding {

  val GzippedEncoding: ContentEncoding = ContentEncoding("gzip")

  val IdentityEncoding: ContentEncoding = ContentEncoding("identity")

  def encodeUtf8(string: String): Array[Byte] = string.getBytes(StandardCharsets.UTF_8)

  def decodeUtf8(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  def gzip(inBytes: Array[Byte]): Array[Byte] = {
    val byteOutputStream = new ByteArrayOutputStream
    val gzipOutputStream = new GZIPOutputStream(byteOutputStream)
    gzipOutputStream.write(inBytes)
    gzipOutputStream.close()
    val outBytes = byteOutputStream.toByteArray
    byteOutputStream.close()
    outBytes
  }

  def ungzip(inBytes: Array[Byte]): Either[ZipException, Array[Byte]] =
    Either.catchOnly[ZipException] {
      val byteInputStream = new ByteArrayInputStream(inBytes)
      val gzipInputStream = new GZIPInputStream(byteInputStream)
      byteInputStream.close()
      val outBytes = java8ReadAllBytes(gzipInputStream)
      gzipInputStream.close()
      outBytes
    }

  private def java8ReadAllBytes(gzipInputStream: GZIPInputStream): Array[Byte] = {
    val buffer = new Array[Byte](1024)
    val byteOutputStream = new ByteArrayOutputStream()
    var len = 0
    while ({
      len = gzipInputStream.read(buffer)
      len > 0
    }) {
      byteOutputStream.write(buffer, 0, len)
    }
    val bytes = byteOutputStream.toByteArray
    byteOutputStream.close()
    bytes
  }
}
