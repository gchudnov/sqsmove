package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup
import com.github.gchudnov.sqsmove.BuildInfo as AppBuildInfo
import scopt.OEffect.ReportError
import scopt.{ OEffect, OParser, OParserSetup }
import zio.*
import java.io.File
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup.*

/**
 * Intermediate Arguments used on parsing the input Arguments
 */
final case class SqsArgs(
  srcQueueName: Option[String],
  dstQueueName: Option[String],
  srcDir: Option[File],
  dstDir: Option[File],
  parallelism: Int,
  visibilityTimeout: Duration,
  isNoDelete: Boolean,
  isVerbose: Boolean
)

object SqsArgs:

  val DefaultParallelism       = 16
  val DefaultVisibilityTimeout = 30.seconds
  val DefaultNoDelete          = false
  val DefaultVerbose           = false

  def empty: SqsArgs = SqsArgs(
    srcQueueName = None,
    dstQueueName = None,
    srcDir = None,
    dstDir = None,
    parallelism = DefaultParallelism,
    visibilityTimeout = DefaultVisibilityTimeout,
    isNoDelete = DefaultNoDelete,
    isVerbose = DefaultVerbose
  )

final case class SqsConfig(
  source: Either[String, File],
  destination: Either[String, File],
  parallelism: Int,
  visibilityTimeout: Duration,
  isNoDelete: Boolean,
  isVerbose: Boolean
)

object SqsConfig:

  private val ArgHelpShort             = 'h'
  private val ArgHelpLong              = "help"
  private val ArgSrcQueueShort         = 's'
  private val ArgSrcQueueLong          = "src-queue"
  private val ArgDstQueueShort         = 'd'
  private val ArgDstQueueLong          = "dst-queue"
  private val ArgSrcDirLong            = "src-dir"
  private val ArgDstDirLong            = "dst-dir"
  private val ArgParallelismShort      = 'p'
  private val ArgParallelismLong       = "parallelism"
  private val ArgVisibilityTimeoutLong = "visibility-timeout"
  private val ArgNoDeleteLong          = "no-delete"
  private val ArgVerboseShort          = 'v'
  private val ArgVerboseLong           = "verbose"

  private val argsBuilder = OParser.builder[SqsArgs]
  private val argsParser =
    import argsBuilder.*
    OParser.sequence(
      programName(AppBuildInfo.name),
      head(AppBuildInfo.name, AppBuildInfo.version),
      opt[String](ArgSrcQueueShort, ArgSrcQueueLong)
        .optional()
        .valueName("<name>")
        .action((x, c) => c.copy(srcQueueName = Some(x)))
        .text("source queue name"),
      opt[String](ArgDstQueueShort, ArgDstQueueLong)
        .optional()
        .valueName("<name>")
        .action((x, c) => c.copy(dstQueueName = Some(x)))
        .text("destination queue name"),
      opt[File](ArgSrcDirLong)
        .optional()
        .valueName("<path>")
        .action((x, c) => c.copy(srcDir = Some(x)))
        .text("source directory path"),
      opt[File](ArgDstDirLong)
        .optional()
        .valueName("<path>")
        .action((x, c) => c.copy(dstDir = Some(x)))
        .text("destination directory path"),
      opt[Int](ArgParallelismShort, ArgParallelismLong)
        .optional()
        .valueName("<value>")
        .validate(n => if n >= 0 then Right(()) else Left(s"$ArgParallelismLong cannot be negative"))
        .action((x, c) => c.copy(parallelism = x))
        .text(s"parallelism (default: ${SqsArgs.DefaultParallelism})"),
      opt[Int](ArgVisibilityTimeoutLong)
        .optional()
        .valueName("<value>")
        .validate(n => if n >= 0 then Right(()) else Left(s"$ArgVisibilityTimeoutLong cannot be negative"))
        .action((x, c) => c.copy(parallelism = x))
        .text(s"visibility timeout (default: ${SqsArgs.DefaultVisibilityTimeout})"),
      opt[Unit](ArgNoDeleteLong)
        .optional()
        .text("do not delete message after processing")
        .action((_, c) => c.copy(isNoDelete = true)),
      opt[Unit](ArgVerboseShort, ArgVerboseLong)
        .optional()
        .action((_, c) => c.copy(isVerbose = true))
        .text("verbose output"),
      opt[Unit](ArgHelpShort, ArgHelpLong)
        .optional()
        .text("prints this usage text")
        .validate(_ => Left(OEffectHelpKey)),
      checkConfig(c =>
        for
          _ <- validateQueueOrDir(c.srcQueueName, c.srcDir)(List(ArgSrcQueueShort.toString, ArgSrcQueueLong), List(ArgSrcDirLong))
          _ <- validateQueueOrDir(c.dstQueueName, c.dstDir)(List(ArgDstQueueShort.toString, ArgDstQueueLong), List(ArgDstDirLong))
        yield ()
      )
    )

  private val OEffectPrefix  = "OEFFECT"
  private val OEffectHelpKey = s"$OEffectPrefix:HELP"

  def fromArgs(args: List[String])(argParserSetup: OParserSetup): RIO[Has[OZEffectSetup], SqsConfig] =
    OParser.runParser(argsParser, args, SqsArgs.empty, argParserSetup) match
      case (result, effects) =>
        for
          pEffects <- preprocessOEffects(effects)
          _        <- runOEffects(pEffects)
          aConfig  <- ZIO.fromOption(result).orElseFail(new IllegalArgumentException(s"Use --$ArgHelpLong for more information."))
          config <- (for
                      source      <- queueOrDir(aConfig.srcQueueName, aConfig.srcDir)("source")
                      destination <- queueOrDir(aConfig.dstQueueName, aConfig.dstDir)("destination")
                    yield SqsConfig(
                      source = source,
                      destination = destination,
                      parallelism = aConfig.parallelism,
                      visibilityTimeout = aConfig.visibilityTimeout,
                      isNoDelete = aConfig.isNoDelete,
                      isVerbose = aConfig.isVerbose
                    ))
        yield config

  private def preprocessOEffects(effects: List[OEffect]): RIO[Has[OZEffectSetup], List[OEffect]] =
    val hasHelp = hasKey(OEffectHelpKey)(effects)

    if hasHelp then displayToOut(usage()) *> ZIO.fail(new SuccessExitException())
    else ZIO(effects.filterNot(it => it.isInstanceOf[ReportError] && it.asInstanceOf[ReportError].msg.startsWith(OEffectPrefix)))

  private def hasKey(key: String)(effects: List[OEffect]): Boolean =
    effects.exists {
      case ReportError(msg) if (msg == key) => true
      case _                                => false
    }

  def usage(): String =
    OParser.usage(argsParser)

  def validateQueueOrDir(queueName: Option[String], dir: Option[File])(queueArgs: List[String], dirArgs: List[String]): Either[String, Unit] =
    (queueName, dir) match
      case (Some(_), Some(_)) => Left(s"Queue name (${queueArgs.mkString(",")}) and directory (${dirArgs.mkString(",")}) cannot be specified at the same time.")
      case (None, None)       => Left(s"Queue name (${queueArgs.mkString(",")}) or directory (${dirArgs.mkString(",")}) should be specified.")
      case (_, _)             => Right[String, Unit](())

  def queueOrDir(queueName: Option[String], dir: Option[File])(name: String): ZIO[Any, Throwable, Either[String, File]] =
    (queueName, dir) match
      case (Some(x), None)    => ZIO.succeed(Left[String, File](x))
      case (None, Some(x))    => ZIO.succeed(Right[String, File](x))
      case (Some(_), Some(_)) => ZIO.fail(new IllegalArgumentException(s"Both ${name} queue and directory cannot be specified at the same time."))
      case (None, None)       => ZIO.fail(new IllegalArgumentException(s"Neither ${name} queue nor directory was specified."))
