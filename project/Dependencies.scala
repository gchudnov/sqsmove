import sbt._

object Dependencies {

  object versions {
    val awsSdk        = "2.18.30"
    val logback       = "1.4.5"
    val scopt         = "4.1.0"
    val zio           = "2.0.4"
    val scalaCsv      = "1.3.10"
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

  private val scalaCsv = "com.github.tototoshi" %% "scala-csv" % versions.scalaCsv

  val sqsMove: Seq[ModuleID] = {
    val compile = Seq(
      awsNetty,
      awsSqs,
      scopt,
      zio,
      zioStreams,
      logback,
      scalaCsv
    ) map (_ % "compile")
    val test = Seq(zioTest, zioTestSbt, zioTestMagnolia) map (_ % "test")
    compile ++ test
  }
}
