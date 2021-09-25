package com.github.gchudnov.sqsmove

import com.github.gchudnov.sqsmove.zopt.SuccessExitException
import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.{ displayToOut, runOEffects, OZEffectSetup }
import scopt.OEffect.ReportError
import scopt.{ OEffect, OParser, OParserSetup }
import zio.*
import java.io.File

final case class SqsConfig(
  srcQueueName: String,
  destination: Either[String, File],
  n: Int,
  isVerbose: Boolean
)

object SqsConfig:

  val empty: SqsConfig = SqsConfig(srcQueueName = "", destination = Left(""), n = 16, isVerbose = false)

  private val ArgHelpShort        = 'h'
  private val ArgHelpLong         = "help"
  private val ArgSrcQueueShort    = 's'
  private val ArgSrcQueueLong     = "src-queue"
  private val ArgDstQueueShort    = 'd'
  private val ArgDstQueueLong     = "dst-queue"
  private val ArgDstDirLong       = "dst-dir"
  private val ArgParallelismShort = 'p'
  private val ArgParallelismLong  = "parallelism"
  private val ArgVerboseShort     = 'v'
  private val ArgVerboseLong      = "verbose"

  private val argsBuilder = OParser.builder[SqsConfig]
  private val argsParser =
    import argsBuilder.*
    OParser.sequence(
      programName(BuildInfo.name),
      head(BuildInfo.name, BuildInfo.version),
      opt[String](ArgSrcQueueShort, ArgSrcQueueLong)
        .required()
        .valueName("<name>")
        .action((x, c) => c.copy(srcQueueName = x))
        .text("source queue name"),
      opt[String](ArgDstQueueShort, ArgDstQueueLong)
        .required()
        .valueName("<name>")
        .action((x, c) => c.copy(destination = Left(x)))
        .text("destination queue name"),
      opt[File](ArgDstDirLong)
        .required()
        .valueName("<path>")
        .action((x, c) => c.copy(destination = Right(x)))
        .text("destination directory path"),
      opt[Int](ArgParallelismShort, ArgParallelismLong)
        .optional()
        .valueName("<value>")
        .validate(n => if n >= 0 then Right(()) else Left(s"$ArgParallelismLong cannot be negative"))
        .action((x, c) => c.copy(n = x))
        .text(s"parallelism (default: ${SqsConfig.empty.n})"),
      opt[Unit](ArgHelpShort, ArgHelpLong)
        .optional()
        .text("prints this usage text")
        .validate(_ => Left(OEffectHelpKey)),
      opt[Unit](ArgVerboseShort, ArgVerboseLong)
        .optional()
        .action((_, c) => c.copy(isVerbose = true))
        .text("verbose output")
    )

  private val OEffectPrefix  = "OEFFECT"
  private val OEffectHelpKey = s"$OEffectPrefix:HELP"

  def fromArgs(args: List[String])(argParserSetup: OParserSetup): RIO[OZEffectSetup, SqsConfig] =
    OParser.runParser(argsParser, args, SqsConfig.empty, argParserSetup) match
      case (result, effects) =>
        for
          peffects <- preprocessOEffects(effects)
          _        <- runOEffects(peffects)
          config   <- ZIO.fromOption(result).orElseFail(new IllegalArgumentException(s"Use --$ArgHelpLong for more information."))
        yield config

  private def preprocessOEffects(effects: List[OEffect]): RIO[OZEffectSetup, List[OEffect]] =
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
