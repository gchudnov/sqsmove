package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.sqs.BasicSqsMove.monitor
import com.github.gchudnov.sqsmove.sqs.{ copy, getQueueUrl, Sqs }
import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup
import scopt.{ DefaultOParserSetup, OParserSetup }
import zio.*
import zio.zio.Clock
import zio.zio.Console.*

import java.lang.System as JSystem

object SqsMove extends ZIOAppDefault {

  private val sqsMaxConcurrency: Int = 512

  override def run: ZIO[Environment with ZEnv with Has[ZIOAppArgs], Any, Any] = {
    val osetup: ZLayer[Has[Console], Throwable, OZEffectSetup] = makeOZEffectSetup()
    val psetup: OParserSetup                                   = makePEffectSetup()

    val program = for {
      as  <- args
      cfg <- SqsConfig.fromArgs(as.toList)(psetup).provideSomeLayer[Has[Console]](osetup)
      env  = makeEnv(cfg)
      _   <- makeProgram(cfg).provideSomeLayer[Has[Clock] with Has[Console]](env)
    } yield ()

    program.catchSome { case _: SuccessExitException => ZIO.unit }
      .tapError(t => printLineError(s"Error: ${t.getMessage}"))
  }

  private def makeProgram(cfg: SqsConfig): ZIO[Sqs with Has[Clock] with Has[Console], Throwable, Unit] =
    for {
      srcQueueUrl <- getQueueUrl(cfg.srcQueueName)
      dstQueueUrl <- getQueueUrl(cfg.dstQueueName)
      _           <- monitor()
      _           <- copy(srcQueueUrl, dstQueueUrl)
    } yield ()

  private def makeOZEffectSetup(): ZLayer[Has[Console], Nothing, OZEffectSetup] =
    OZEffectSetup.stdio

  private def makePEffectSetup(): OParserSetup =
    new DefaultOParserSetup with OParserSetup {
      override def errorOnUnknownArgument: Boolean   = false
      override def showUsageOnError: Option[Boolean] = Some(false)
    }

  private def makeEnv(cfg: SqsConfig): ZLayer[Has[Clock], Throwable, Sqs] = {
    val clockEnv = Clock.any
    val copyEnv = cfg.n match {
      case 0 => Sqs.auto(maxConcurrency = sqsMaxConcurrency, initParallelism = 1)
      case 1 => Sqs.serial(maxConcurrency = sqsMaxConcurrency)
      case m => Sqs.parallel(maxConcurrency = sqsMaxConcurrency, parallelism = m)
    }
    val appEnv = clockEnv >>> copyEnv

    appEnv
  }

}
