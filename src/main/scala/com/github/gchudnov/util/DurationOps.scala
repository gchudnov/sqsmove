package com.github.gchudnov.sqsmove.util

import zio.*
import java.util.concurrent.TimeUnit
import scala.util.matching.Regex

import scala.util.control.Exception.*

object DurationOps:

  /**
   * Ensure that the string contains a parsable duration
   */
  def ensure(s: String): Either[Throwable, Unit] =
    val rx = """\d+[dhms]""".r
    rx.findFirstIn(s) match
      case Some(_) => Right(())
      case None    => Left(new IllegalArgumentException(s"Cannot extract days, hours, minutes or seconds from '${s}'"))

  /**
   * Parses the duration
   */
  def parse(s: String): Either[Throwable, Duration] =
    for
      _ <- ensure(s)
      d <- extract(s, mkRegex('d')).map(Duration(_, TimeUnit.DAYS))
      h <- extract(s, mkRegex('h')).map(Duration(_, TimeUnit.HOURS))
      m <- extract(s, mkRegex('m')).map(Duration(_, TimeUnit.MINUTES))
      s <- extract(s, mkRegex('s')).map(Duration(_, TimeUnit.SECONDS))
    yield (d + h + m + s)

  private def extract(s: String, rx: Regex): Either[Throwable, Int] = allCatch.either {
    val x = rx.findFirstMatchIn(s).map(_.group(1))
    rx.findFirstMatchIn(s).map(_.group(1)).map(_.toInt).getOrElse(0)
  }

  private def mkRegex(c: Char): Regex =
    s"""(\\d+)${c}""".r
