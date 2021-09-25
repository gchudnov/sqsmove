package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.sqs.BasicSqsMove.monitor
import com.github.gchudnov.sqsmove.sqs.{ download, getQueueUrl, move, Sqs }
import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup
import scopt.{ DefaultOParserSetup, OParserSetup }
import zio.*
import zio.Clock
import zio.Console.*
import java.io.File

import java.lang.System as JSystem

object SqsMove extends ZIOAppDefault:

  private val sqsMaxConcurrency: Int = 512

  override def run: ZIO[Environment with ZEnv with Has[ZIOAppArgs], Any, Any] =
    val osetup: ZLayer[Has[Console], Throwable, OZEffectSetup] = makeOZEffectSetup()
    val psetup: OParserSetup                                   = makePEffectSetup()

    val program = for
      as  <- args
      cfg <- SqsConfig.fromArgs(as.toList)(psetup).provideSomeLayer[Has[Console]](osetup)
      env  = makeEnv(cfg)
      _   <- makeProgram(cfg).provideSomeLayer[Has[Clock] with Has[Console]](env)
    yield ()

    program.catchSome { case _: SuccessExitException => ZIO.unit }
      .tapError(t => printLineError(s"Error: ${t.getMessage}"))

  private def makeProgram(cfg: SqsConfig): ZIO[Sqs with Has[Clock] with Has[Console], Throwable, Unit] =
    cfg.destination.fold(
      dstQueueName => makeQueueMoveProgram(cfg.srcQueueName, dstQueueName),
      dstDir => makeDirMoveProgram(cfg.srcQueueName, dstDir)
    )

  private def makeQueueMoveProgram(srcQueueName: String, dstQueueName: String): ZIO[Sqs with Has[Clock] with Has[Console], Throwable, Unit] =
    for
      srcQueueUrl <- getQueueUrl(srcQueueName)
      dstQueueUrl <- getQueueUrl(dstQueueName)
      _           <- monitor()
      _           <- move(srcQueueUrl, dstQueueUrl)
    yield ()

  private def makeDirMoveProgram(srcQueueName: String, dstDir: File): ZIO[Sqs with Has[Clock] with Has[Console], Throwable, Unit] =
    for
      srcQueueUrl <- getQueueUrl(srcQueueName)
      _           <- monitor()
      _           <- download(srcQueueUrl, dstDir)
    yield ()

  private def makeOZEffectSetup(): ZLayer[Has[Console], Nothing, OZEffectSetup] =
    OZEffectSetup.stdio

  private def makePEffectSetup(): OParserSetup =
    new DefaultOParserSetup with OParserSetup:
      override def errorOnUnknownArgument: Boolean   = false
      override def showUsageOnError: Option[Boolean] = Some(false)

  private def makeEnv(cfg: SqsConfig): ZLayer[Has[Clock], Throwable, Sqs] =
    val clockEnv = Clock.any
    val copyEnv = cfg.n match
      case 0 => Sqs.auto(maxConcurrency = sqsMaxConcurrency, initParallelism = 1)
      case 1 => Sqs.serial(maxConcurrency = sqsMaxConcurrency)
      case m => Sqs.parallel(maxConcurrency = sqsMaxConcurrency, parallelism = m)
    val appEnv = clockEnv >>> copyEnv

    appEnv
