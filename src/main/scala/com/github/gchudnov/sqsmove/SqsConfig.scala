package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup
import com.github.gchudnov.sqsmove.BuildInfo as AppBuildInfo
import scopt.OEffect.ReportError
import scopt.{ OEffect, OParser, OParserSetup }
import zio.*
import java.io.File
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.OZEffectSetup.*
import com.github.gchudnov.sqsmove.util.DurationOps

/**
 * Intermediate Arguments used on parsing the input Arguments
 */
final case class SqsArgs(
  srcQueueName: Option[String],
  dstQueueName: Option[String],
  srcDir: Option[File],
  dstDir: Option[File],
  parallelism: Int,
  visibilityTimeout: String,
  isDelete: Boolean,
  isAsk: Boolean,
  isVerbose: Boolean
)

object SqsArgs:

  private[sqsmove] val DefaultParallelism       = 16
  private[sqsmove] val DefaultVisibilityTimeout = "30s" // 30 seconds
  private[sqsmove] val DefaultDelete            = true
  private[sqsmove] val DefaultAsk               = true
  private[sqsmove] val DefaultVerbose           = false

  def empty: SqsArgs = SqsArgs(
    srcQueueName = None,
    dstQueueName = None,
    srcDir = None,
    dstDir = None,
    parallelism = DefaultParallelism,
    visibilityTimeout = DefaultVisibilityTimeout,
    isDelete = DefaultDelete,
    isAsk = DefaultAsk,
    isVerbose = DefaultVerbose
  )

final case class SqsConfig(
  source: Either[String, File],
  destination: Either[String, File],
  parallelism: Int,
  visibilityTimeout: Duration,
  isDelete: Boolean,
  isAsk: Boolean,
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
  private val ArgNoAskLong             = "yes"
  private val ArgVerboseShort          = 'v'
  private val ArgVerboseLong           = "verbose"
  private val ArgVersionLong           = "version"

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
      opt[String](ArgVisibilityTimeoutLong)
        .optional()
        .valueName("<value>")
        .validate(x => DurationOps.ensure(x).left.map(_.getMessage))
        .action((x, c) => c.copy(visibilityTimeout = x))
        .text(s"visibility timeout (default: ${SqsArgs.DefaultVisibilityTimeout}). Format: 1d12h35m16s"),
      opt[Unit](ArgNoDeleteLong)
        .optional()
        .text("do not delete messages after processing")
        .action((_, c) => c.copy(isDelete = false)),
      opt[Unit](ArgNoAskLong)
        .optional()
        .text("do not ask for confirmation")
        .action((_, c) => c.copy(isAsk = false)),
      opt[Unit](ArgVerboseShort, ArgVerboseLong)
        .optional()
        .action((_, c) => c.copy(isVerbose = true))
        .text("verbose output"),
      opt[Unit](ArgHelpShort, ArgHelpLong)
        .optional()
        .text("prints this usage text")
        .validate(_ => Left(OEffectHelpKey)),
      opt[Unit](ArgVersionLong)
        .optional()
        .text("prints the version")
        .validate(_ => Left(OEffectVersionKey)),
      note("""
             |Examples:
             |
             |  - Move messages from queue A to queue B:
             |    sqsmove -s A -d B
             |
             |  - Move messages from queue A to queue B with parallelism 1:
             |    sqsmove -s A -d B -p 1
             |
             |  - Copy messages from queue A to queue B with visibility timeout 5m:
             |    sqsmove -s A -d B --no-delete --visibility-timeout=5m
             |
             |  - Download messages to directory D:
             |    sqsmove -s A --dst-dir D
             |
             |  - Upload messages from directory D:
             |    sqsmove --src-dir D -d B
             |""".stripMargin),
      checkConfig(c =>
        for
          _ <- validateQueueOrDir("source")(c.srcQueueName, c.srcDir)
          _ <- validateQueueOrDir("destination")(c.dstQueueName, c.dstDir)
        yield ()
      )
    )

  private val OEffectPrefix     = "OEFFECT"
  private val OEffectHelpKey    = s"$OEffectPrefix:HELP"
  private val OEffectVersionKey = s"$OEffectPrefix:VERSION"

  def fromArgs(args: List[String])(argParserSetup: OParserSetup): RIO[Has[OZEffectSetup], SqsConfig] =
    OParser.runParser(argsParser, args, SqsArgs.empty, argParserSetup) match
      case (result, effects) =>
        for
          pEffects <- preprocessOEffects(effects)
          _        <- runOEffects(pEffects)
          aConfig  <- ZIO.fromOption(result).orElseFail(new IllegalArgumentException(s"Use --$ArgHelpLong for more information."))
          config <- (for
                      source            <- queueOrDir(aConfig.srcQueueName, aConfig.srcDir)("source")
                      destination       <- queueOrDir(aConfig.dstQueueName, aConfig.dstDir)("destination")
                      visibilityTimeout <- ZIO.fromEither(DurationOps.parse(aConfig.visibilityTimeout))
                    yield SqsConfig(
                      source = source,
                      destination = destination,
                      parallelism = aConfig.parallelism,
                      visibilityTimeout = visibilityTimeout,
                      isDelete = aConfig.isDelete,
                      isAsk = aConfig.isAsk,
                      isVerbose = aConfig.isVerbose
                    ))
        yield config

  private def preprocessOEffects(effects: List[OEffect]): RIO[Has[OZEffectSetup], List[OEffect]] =
    val hasHelp    = hasKey(OEffectHelpKey)(effects)
    val hasVersion = hasKey(OEffectVersionKey)(effects)

    if hasHelp || hasVersion then
      val value = (hasHelp, hasVersion) match
        case (true, _) =>
          usage()
        case (false, true) =>
          version()
        case (_, _) =>
          ""
      displayToOut(value) *> ZIO.fail(new SuccessExitException())
    else ZIO(effects.filterNot(it => it.isInstanceOf[ReportError] && it.asInstanceOf[ReportError].msg.startsWith(OEffectPrefix)))

  private def hasKey(key: String)(effects: List[OEffect]): Boolean =
    effects.exists {
      case ReportError(msg) if (msg == key) => true
      case _                                => false
    }

  def usage(): String =
    OParser.usage(argsParser)

  def version(): String =
    s"${AppBuildInfo.name} ${AppBuildInfo.version}"

  def validateQueueOrDir(useCase: String)(queueName: Option[String], dir: Option[File]): Either[String, Unit] =
    val (queueOpts, dirOpts) = useCase match
      case "source" =>
        (List("-" + ArgSrcQueueShort.toString, "--" + ArgSrcQueueLong), List("--" + ArgSrcDirLong))
      case "destination" =>
        (List("-" + ArgDstQueueShort.toString, "--" + ArgDstQueueLong), List("--" + ArgDstDirLong))

    (queueName, dir) match
      case (Some(_), Some(_)) =>
        Left(s"Queue ${queueOpts.mkString("[", ",", "]")} and directory options ${dirOpts.mkString("[", ",", "]")} cannot be specified at the same time for a ${useCase}.")
      case (None, None) =>
        Left(s"Please specify queue ${queueOpts.mkString("[", ",", "]")} or directory options ${dirOpts.mkString("[", ",", "]")} for a ${useCase}.")
      case (_, _) =>
        Right[String, Unit](())

  def queueOrDir(queueName: Option[String], dir: Option[File])(name: String): ZIO[Any, Throwable, Either[String, File]] =
    (queueName, dir) match
      case (Some(x), None)    => ZIO.succeed(Left[String, File](x))
      case (None, Some(x))    => ZIO.succeed(Right[String, File](x))
      case (Some(_), Some(_)) => ZIO.fail(new IllegalArgumentException(s"Both ${name} queue and directory cannot be specified at the same time."))
      case (None, None)       => ZIO.fail(new IllegalArgumentException(s"Neither ${name} queue nor directory was specified."))
