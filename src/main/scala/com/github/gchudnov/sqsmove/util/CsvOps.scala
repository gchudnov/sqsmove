package com.github.gchudnov.sqsmove.util

import com.github.tototoshi.csv.*

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import scala.io.Source as SSource
import scala.util.control.Exception.*

object CsvOps:

  implicit object platformFormat extends DefaultCSVFormat:
    override val lineTerminator = sys.props("line.separator")

  def tableToString(t: List[List[String]]): Either[Throwable, String] =
    allCatch.either {
      val os     = new ByteArrayOutputStream()
      val writer = CSVWriter.open(os)
      writer.writeAll(t)
      os.toString(StandardCharsets.UTF_8)
    }

  def tableFromString(s: String): Either[Throwable, List[List[String]]] =
    if s.isEmpty then Right[Throwable, List[List[String]]](List.empty[List[String]])
    else
      val reader = CSVReader.open(SSource.fromString(s))
      allCatch.either(reader.all())
