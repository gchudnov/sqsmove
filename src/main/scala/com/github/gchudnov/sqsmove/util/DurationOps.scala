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
   * Parses the duration expressed as 1d15h
   */
  def parse(s: String): Either[Throwable, Duration] =
    for
      _ <- ensure(s)
      d <- extract(s, mkRegex('d')).map(Duration(_, TimeUnit.DAYS))
      h <- extract(s, mkRegex('h')).map(Duration(_, TimeUnit.HOURS))
      m <- extract(s, mkRegex('m')).map(Duration(_, TimeUnit.MINUTES))
      s <- extract(s, mkRegex('s')).map(Duration(_, TimeUnit.SECONDS))
    yield (d + h + m + s)

  /**
   * Converts duration to a strung, e.g. 1d15h
   */
  def asString(dt: Duration): String =
    val d = dt.toDaysPart
    val h = dt.toHoursPart
    val m = dt.toMinutesPart
    val s = dt.toSecondsPart

    val sd = if d > 0 then s"${d}d" else ""
    val sh = if h > 0 then s"${h}h" else ""
    val sm = if m > 0 then s"${m}m" else ""
    val ss = if s > 0 then s"${s}s" else ""

    s"${sd}${sh}${sm}${ss}"

  private def extract(s: String, rx: Regex): Either[Throwable, Int] = allCatch.either {
    val x = rx.findFirstMatchIn(s).map(_.group(1))
    rx.findFirstMatchIn(s).map(_.group(1)).map(_.toInt).getOrElse(0)
  }

  private def mkRegex(c: Char): Regex =
    s"""(\\d+)${c}""".r
