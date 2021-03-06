package com.github.gchudnov.sqsmove.zopt.ozeffectsetup

import com.github.gchudnov.sqsmove.zopt.{ FailureExitException, SuccessExitException }
import zio.*
import zio.Console.*

final class StdioEffectSetup() extends OZEffectSetup:

  override def displayToOut(msg: String): Task[Unit] =
    printLine(msg)

  override def displayToErr(msg: String): Task[Unit] =
    printLineError(msg)

  override def reportError(msg: String): Task[Unit] =
    displayToErr("Error: " + msg)

  override def reportWarning(msg: String): Task[Unit] =
    displayToErr("Warning: " + msg)

  override def terminate(exitState: Either[String, Unit]): Task[Unit] =
    exitState match
      case Left(_)  => ZIO.fail(new FailureExitException())
      case Right(_) => ZIO.fail(new SuccessExitException())

object StdioEffectSetup:
  def layer: ZLayer[Any, Nothing, OZEffectSetup] =
    ZLayer.succeed(new StdioEffectSetup())
