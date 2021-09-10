package com.github.gchudnov.sqsmove.zopt

import scopt.OEffect
import scopt.OEffect._
import zio.Has
import zio.RIO
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.console.Console

package object ozeffectsetup {
  type OZEffectSetup = Has[OZEffectSetup.Service]

  object OZEffectSetup {
    trait Service {
      def displayToOut(msg: String): Task[Unit]
      def displayToErr(msg: String): Task[Unit]
      def reportError(msg: String): Task[Unit]
      def reportWarning(msg: String): Task[Unit]
      def terminate(exitState: Either[String, Unit]): Task[Unit]
    }

    val any: ZLayer[OZEffectSetup, Nothing, OZEffectSetup] =
      ZLayer.requires[OZEffectSetup]

    val stdio: ZLayer[Console, Throwable, OZEffectSetup] = ZLayer.fromService { console =>
      new Service {
        override def displayToOut(msg: String): Task[Unit] =
          console.putStrLn(msg)

        override def displayToErr(msg: String): Task[Unit] =
          console.putStrLnErr(msg)

        override def reportError(msg: String): Task[Unit] =
          displayToErr("Error: " + msg)

        override def reportWarning(msg: String): Task[Unit] =
          displayToErr("Warning: " + msg)

        override def terminate(exitState: Either[String, Unit]): Task[Unit] =
          exitState match {
            case Left(_)  => ZIO.fail(new FailureExitException())
            case Right(_) => ZIO.fail(new SuccessExitException())
          }
      }
    }
  }

  def displayToOut(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.accessM(_.get.displayToOut(msg))

  def displayToErr(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.accessM(_.get.displayToErr(msg))

  def reportError(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.accessM(_.get.reportError(msg))

  def reportWarning(msg: String): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.accessM(_.get.reportWarning(msg))

  def terminate(exitState: Either[String, Unit]): ZIO[OZEffectSetup, Throwable, Unit] =
    ZIO.accessM(_.get.terminate(exitState))

  def runOEffects(effects: List[OEffect]): RIO[OZEffectSetup, Unit] =
    ZIO
      .foreach(effects) {
        case DisplayToOut(msg)    => displayToOut(msg)
        case DisplayToErr(msg)    => displayToErr(msg)
        case ReportError(msg)     => reportError(msg)
        case ReportWarning(msg)   => reportWarning(msg)
        case Terminate(exitState) => terminate(exitState)
      }
      .unit
}
