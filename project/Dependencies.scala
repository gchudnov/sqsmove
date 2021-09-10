import sbt._

object Dependencies {

  object versions {
    val awsSdk        = "2.17.37"
    val kindProjector = "0.10.3"
    val logback       = "1.2.6"
    val scopt         = "4.0.1"
    val zio           = "1.0.11"
    val zioLogging    = "0.5.12"
    val zioZmx        = "0.0.8"
  }

  // compiler plugins
  private val kindProjector = compilerPlugin("org.typelevel" %% "kind-projector" % versions.kindProjector)

  private val compiler = Seq(
    kindProjector
  )

  private val scopt = "com.github.scopt" %% "scopt" % versions.scopt

  private val logback = "ch.qos.logback" % "logback-classic" % versions.logback

  private val zioLogging      = "dev.zio" %% "zio-logging"       % versions.zioLogging
  private val zioLoggingSlf4j = "dev.zio" %% "zio-logging-slf4j" % versions.zioLogging

  private val awsSqs   = "software.amazon.awssdk" % "sqs"              % versions.awsSdk
  private val awsNetty = "software.amazon.awssdk" % "netty-nio-client" % versions.awsSdk

  private val zio        = "dev.zio" %% "zio"         % versions.zio
  private val zioStreams = "dev.zio" %% "zio-streams" % versions.zio
  private val zioZmx     = "dev.zio" %% "zio-zmx"     % versions.zioZmx

  private val zioTest         = "dev.zio" %% "zio-test"          % versions.zio
  private val zioTestSbt      = "dev.zio" %% "zio-test-sbt"      % versions.zio
  private val zioTestMagnolia = "dev.zio" %% "zio-test-magnolia" % versions.zio

  val sqsCopy: Seq[ModuleID] = {
    val compile = Seq(
      awsNetty,
      awsSqs,
      scopt,
      zio,
      zioStreams,
      zioZmx,
      logback,
      zioLogging,
      zioLoggingSlf4j
    ) map (_ % "compile")
    val test = Seq(zioTest, zioTestSbt, zioTestMagnolia) map (_ % "test")
    compile ++ test ++ compiler
  }
}
