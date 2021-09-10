package com.github.gchudnov.sqsmove.zopt

sealed abstract class ExitException(val code: Int) extends RuntimeException("")

final class SuccessExitException() extends ExitException(0)
final class FailureExitException() extends ExitException(1)

object ExitException {
  def apply(t: Throwable): Boolean = t match {
    case _: ExitException => true
    case _                => false
  }

  def unapply(t: Throwable): Option[Throwable] = Some(t).filter(apply)
}
