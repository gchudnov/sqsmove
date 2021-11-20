package com.github.gchudnov.sqsmove.zopt.ozeffectsetup

import scopt.OEffect
import scopt.OEffect.*
import zio.*

trait OZEffectSetup:
  def displayToOut(msg: String): Task[Unit]
  def displayToErr(msg: String): Task[Unit]
  def reportError(msg: String): Task[Unit]
  def reportWarning(msg: String): Task[Unit]
  def terminate(exitState: Either[String, Unit]): Task[Unit]

object OZEffectSetup:
  def displayToOut(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.serviceWithZIO[OZEffectSetup](_.displayToOut(msg))

  def displayToErr(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.serviceWithZIO[OZEffectSetup](_.displayToErr(msg))

  def reportError(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.serviceWithZIO[OZEffectSetup](_.reportError(msg))

  def reportWarning(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.serviceWithZIO[OZEffectSetup](_.reportWarning(msg))

  def terminate(exitState: Either[String, Unit]): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.serviceWithZIO[OZEffectSetup](_.terminate(exitState))

  def runOEffects(effects: List[OEffect]): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO
      .foreach(effects) {
        case DisplayToOut(msg)    => displayToOut(msg)
        case DisplayToErr(msg)    => displayToErr(msg)
        case ReportError(msg)     => reportError(msg)
        case ReportWarning(msg)   => reportWarning(msg)
        case Terminate(exitState) => terminate(exitState)
      }
      .unit
