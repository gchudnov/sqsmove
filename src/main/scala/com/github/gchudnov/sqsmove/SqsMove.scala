package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.sqs.BasicSqs.{ monitor, summary }
import com.github.gchudnov.sqsmove.sqs.Sqs.*
import com.github.gchudnov.sqsmove.sqs.{ AutoSqs, ParallelSqs, SerialSqs, Sqs }
import com.github.gchudnov.sqsmove.util.DurationOps
import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.{ OZEffectSetup, StdioEffectSetup }
import scopt.{ DefaultOParserSetup, OParserSetup }
import zio.*
import zio.Console.*

import java.io.File
import java.lang.{ RuntimeException, System as JSystem }

object SqsMove extends ZIOAppDefault:

  private val sqsMaxConcurrency: Int = 1024

  override def run: ZIO[Environment with ZEnv with ZIOAppArgs, Any, Any] =
    val osetup: ZLayer[Console, Throwable, OZEffectSetup] = makeOZEffectSetup()
    val psetup: OParserSetup                              = makePEffectSetup()

    val program = for
      as  <- getArgs
      cfg <- SqsConfig.fromArgs(as.toList)(psetup).provideSome[Console](osetup)
      env  = makeEnv(cfg)
      _   <- ask(cfg).when(cfg.isAsk)
      _   <- makeProgram(cfg).provideSome[Clock with Console](env)
    yield ()

    program.catchSome { case _: SuccessExitException => ZIO.unit }
      .tapError(t => printLineError(s"Error: ${t.getMessage}"))
      .catchAll(_ => ZIO.unit)

  private def makeProgram(cfg: SqsConfig): ZIO[Sqs with Clock with Console, Throwable, Unit] =
    (cfg.source, cfg.destination) match
      case (Left(x), Left(y))   => makeMoveProgram(x, y)
      case (Left(x), Right(y))  => makeDownloadProgram(x, y)
      case (Right(x), Left(y))  => makeUploadProgram(x, y)
      case (Right(x), Right(y)) => ZIO.fail(new RuntimeException("Cannot move files between directories. Use 'mv' command instead."))

  private def makeMoveProgram(srcQueueName: String, dstQueueName: String): ZIO[Sqs with Clock with Console, Throwable, Unit] =
    for
      srcQueueUrl <- getQueueUrl(srcQueueName)
      dstQueueUrl <- getQueueUrl(dstQueueName)
      _           <- monitor()
      _           <- move(srcQueueUrl, dstQueueUrl)
      _           <- summary()
    yield ()

  private def makeDownloadProgram(srcQueueName: String, dstDir: File): ZIO[Sqs with Clock with Console, Throwable, Unit] =
    for
      srcQueueUrl <- getQueueUrl(srcQueueName)
      _           <- monitor()
      _           <- download(srcQueueUrl, dstDir)
      _           <- summary()
    yield ()

  private def makeUploadProgram(srcDir: File, dstQueueName: String): ZIO[Sqs with Clock with Console, Throwable, Unit] =
    for
      dstQueueUrl <- getQueueUrl(dstQueueName)
      _           <- monitor()
      _           <- upload(srcDir, dstQueueUrl)
      _           <- summary()
    yield ()

  private def makeOZEffectSetup(): ZLayer[Console, Nothing, OZEffectSetup] =
    StdioEffectSetup.layer

  private def makePEffectSetup(): OParserSetup =
    new DefaultOParserSetup with OParserSetup:
      override def errorOnUnknownArgument: Boolean   = false
      override def showUsageOnError: Option[Boolean] = Some(false)

  private def makeEnv(cfg: SqsConfig): ZLayer[Clock, Throwable, Sqs] =
    val clockEnv = Clock.any
    val copyEnv = cfg.parallelism match
      case 0 => AutoSqs.layer(maxConcurrency = sqsMaxConcurrency, initParallelism = 1, limit = cfg.count, visibilityTimeout = cfg.visibilityTimeout, isDelete = cfg.isDelete)
      case 1 => SerialSqs.layer(maxConcurrency = sqsMaxConcurrency, limit = cfg.count, visibilityTimeout = cfg.visibilityTimeout, isDelete = cfg.isDelete)
      case m => ParallelSqs.layer(maxConcurrency = sqsMaxConcurrency, parallelism = m, limit = cfg.count, visibilityTimeout = cfg.visibilityTimeout, isDelete = cfg.isDelete)
    val appEnv = clockEnv >>> copyEnv

    appEnv

  private def ask(cfg: SqsConfig): ZIO[Console, Throwable, Unit] =
    val isSrcDir   = cfg.source.isRight
    val isSrcQueue = cfg.source.isLeft

    val action      = if (isSrcDir || (isSrcQueue && !cfg.isDelete)) then "COPY" else "MOVE"
    val source      = cfg.source.fold(identity, _.toString)
    val destination = cfg.destination.fold(identity, _.toString)
    val pMsg        = s"parallelism: ${cfg.parallelism}"
    val vMsg        = if isSrcDir then "" else s"visibility-timeout: ${DurationOps.asString(cfg.visibilityTimeout)}"
    val dMsg        = if isSrcDir then "" else s"no-delete: ${!cfg.isDelete}"
    val cMsg        = cfg.count.fold("")(n => s"${n} ")
    val paramsMsg   = List(pMsg, vMsg, dMsg).filter(_.nonEmpty).mkString("; ")
    val msg = s"""Going to ${action} ${cMsg}messages '${source}' -> '${destination}'
                 |[${paramsMsg}]
                 |Are you sure? (y|N)""".stripMargin
    for
      console <- ZIO.service[Console]
      _       <- console.printLine(msg)
      ans     <- console.readLine
      _       <- ZIO.cond(ans == "y", (), new RuntimeException("Aborted"))
    yield ()
