package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.sqs.BasicSqsMove.monitor
import com.github.gchudnov.sqsmove.sqs.{ copy, getQueueUrl, Sqs }
import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup
import scopt.{ DefaultOParserSetup, OParserSetup }
import zio._
import zio.Clock
import zio.Console._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import java.lang.{System => JSystem}

object SqsMove extends App {

  private val sqsMaxConcurrency: Int = 512

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val osetup: ZLayer[Has[Console], Throwable, OZEffectSetup] = makeOZEffectSetup()
    val psetup: OParserSetup                              = makePEffectSetup()

    val program = for {
      cfg <- SqsConfig.fromArgs(args)(psetup).provideSomeLayer[Has[Console]](osetup)
      env  = makeEnv(cfg)
      _   <- makeProgram(cfg).provideSomeLayer[Has[Clock] with Has[Console]](env)
    } yield ()

    program.catchSome { case _: SuccessExitException => ZIO.unit }
      .tapError(t => printLineError(s"Error: ${t.getMessage}"))
      .foldCause(_ => ExitCode.failure, _ => ExitCode.success)
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

  private def makeEnv(cfg: SqsConfig): ZLayer[Has[Clock], Throwable, Logging with Sqs] = {
    val clockEnv = Clock.any
    val logEnv   = makeLogEnv(cfg.isVerbose)
    val copyEnv = cfg.n match {
      case 0 => Sqs.auto(maxConcurrency = sqsMaxConcurrency, initParallelism = 1)
      case 1 => Sqs.serial(maxConcurrency = sqsMaxConcurrency)
      case m => Sqs.parallel(maxConcurrency = sqsMaxConcurrency, parallelism = m)
    }
    val sqsEnv = (logEnv ++ clockEnv) >>> copyEnv

    (logEnv ++ sqsEnv)
  }

  private def makeLogEnv(isVerbose: Boolean): ULayer[Logging] =
    ZIO.when(isVerbose)(ZIO.succeed(JSystem.setProperty("LOG_MODE", "VERBOSE"))).toLayer >>> Slf4jLogger.make(logFormat = (_, logEntry) => logEntry)

}
