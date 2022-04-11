package com.github.gchudnov.sqsmove.zopt

import com.github.gchudnov.sqsmove.zopt.ozeffectsetup.{ OZEffectSetup, StdioEffectSetup }
import scopt.{ DefaultOParserSetup, OParserSetup }
import zio.*

object ZOpt:

  def makeOZEffectSetup(): ZLayer[Any, Nothing, OZEffectSetup] =
    StdioEffectSetup.layer

  def makePEffectSetup(): OParserSetup =
    new DefaultOParserSetup with OParserSetup:
      override def errorOnUnknownArgument: Boolean   = false
      override def showUsageOnError: Option[Boolean] = Some(false)
