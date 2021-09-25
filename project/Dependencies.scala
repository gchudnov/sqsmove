import sbt._

object Dependencies {

  object versions {
    val awsSdk        = "2.17.46"
    val kindProjector = "0.10.3"
    val logback       = "1.2.6"
    val scopt         = "4.0.1"
    val zio           = "2.0.0-M3"
  }

  private val scopt = "com.github.scopt" %% "scopt" % versions.scopt

  private val logback = "ch.qos.logback" % "logback-classic" % versions.logback

  private val awsSqs   = "software.amazon.awssdk" % "sqs"              % versions.awsSdk
  private val awsNetty = "software.amazon.awssdk" % "netty-nio-client" % versions.awsSdk

  private val zio        = "dev.zio" %% "zio"         % versions.zio
  private val zioStreams = "dev.zio" %% "zio-streams" % versions.zio

  private val zioTest         = "dev.zio" %% "zio-test"          % versions.zio
  private val zioTestSbt      = "dev.zio" %% "zio-test-sbt"      % versions.zio
  private val zioTestMagnolia = "dev.zio" %% "zio-test-magnolia" % versions.zio

  val sqsMove: Seq[ModuleID] = {
    val compile = Seq(
      awsNetty,
      awsSqs,
      scopt,
      zio,
      zioStreams,
      logback
    ) map (_ % "compile")
    val test = Seq(zioTest, zioTestSbt, zioTestMagnolia) map (_ % "test")
    compile ++ test
  }
}
