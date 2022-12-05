import sbt.Keys._
import sbt._

Global / cancelable   := true
Global / scalaVersion := Settings.globalScalaVersion
Global / semanticdbEnabled := true

def testFilter(name: String): Boolean = (name endsWith "Spec")

lazy val testSettings = Seq(
  Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  Test / testOptions ++= Seq(Tests.Filter(testFilter))
)

lazy val allSettings = Settings.shared ++ testSettings

lazy val sqsMove = (project in file("."))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, GraalVMNativeImagePlugin)
  .settings(allSettings)
  .settings(
    name := "sqsmove",
    libraryDependencies ++= Dependencies.sqsMove,
    buildInfoKeys                 := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage              := "com.github.gchudnov.sqsmove",
    graalVMNativeImageOptions    ++= Seq("--no-fallback", "--verbose", "--enable-http", "--enable-https", "-H:+PrintClassInitialization", "-H:+AllowIncompleteClasspath", "-H:+ReportExceptionStackTraces") // NOTE: add --dry-run to investigate the build
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("chk", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("plg", "; reload plugins ; libraryDependencies ; reload return")
// NOTE: to use version check for plugins, add to the meta-project (/project/project) sbt-updates.sbt with "sbt-updates" plugin as well.
addCommandAlias("upd", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
