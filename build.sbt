import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.defaultUniversalScript

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
  .enablePlugins(BuildInfoPlugin)
  .settings(allSettings)
  .settings(Settings.assemblySettings)
  .settings(
    name := "sqsmove",
    libraryDependencies ++= Dependencies.sqsMove,
    buildInfoKeys                 := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage              := "com.github.gchudnov.sqsmove",
    assembly / mainClass          := Some("com.github.gchudnov.sqsmove.SqsMove"),
    assembly / assemblyOption     := (assembly / assemblyOption).value.withPrependShellScript(Some(defaultUniversalScript(shebang = false))),
    assembly / assemblyOutputPath := new File(s"./target/${name.value}.jar"),
    assembly / assemblyJarName    := s"${name.value}"
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("chk", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("plg", "; reload plugins ; libraryDependencies ; reload return")
// NOTE: to use version check for plugins, add to the meta-project (/project/project) sbt-updates.sbt with "sbt-updates" plugin as well.
addCommandAlias("upd", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
