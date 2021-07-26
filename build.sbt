import Dependencies.Versions

def crossScalacOptions(scalaVersion: String): Seq[String] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3L, _)) =>
      Seq(
        "-source:3.0-migration",
        "-Xignore-scala2-macros"
      )
    case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
      Seq(
        "-Ydelambdafy:method",
        "-target:jvm-1.8",
        "-Yrangepos",
        "-Ywarn-unused"
      )
  }

lazy val baseSettings = Seq(
  organization := "com.github.j5ik2o",
  homepage := Some(url("https://github.com/j5ik2o/chronos-scheduler-scala")),
  licenses := List("The MIT License" -> url("http://opensource.org/licenses/MIT")),
  developers := List(
    Developer(
      id = "j5ik2o",
      name = "Junichi Kato",
      email = "j5ik2o@gmail.com",
      url = url("https://blog.j5ik2o.me")
    )
  ),
  scalaVersion := Versions.scala213Version,
  scalacOptions ++= (
    Seq(
      "-unchecked",
      "-feature",
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-language:_"
    ) ++ crossScalacOptions(scalaVersion.value)
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  ),
  ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  Test / publishArtifact := false,
  Test / fork := true,
  Test / parallelExecution := false,
  Compile / doc / sources := {
    val old = (Compile / doc / sources).value
    if (scalaVersion.value == Versions.scala3Version) {
      Nil
    } else {
      old
    }
  }
)

val core = (project in file("core"))
  .settings(baseSettings)
  .settings(
    name := "chronos-scheduler-scala-core",
    libraryDependencies ++= Seq(
      "com.github.j5ik2o" %% "chronos-parser-scala" % "1.0.12",
      "org.slf4j"          % "slf4j-api"            % "1.7.32",
      "org.scalatest"     %% "scalatest"            % "3.2.9" % Test,
      "ch.qos.logback"     % "logback-classic"      % "1.2.5" % Test
    )
  )

val AkkaVersion = "2.6.15"

val `akka-actor` = (project in file("akka-actor"))
  .settings(baseSettings)
  .settings(
    name := "chronos-scheduler-scala-akka-actor",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"         % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest"     %% "scalatest"                % "3.2.9"     % Test,
      "ch.qos.logback"     % "logback-classic"          % "1.2.5"     % Test
    )
  ).dependsOn(core)

val `example` = (project in file("example"))
  .settings(baseSettings)
  .settings(
    name := "chronos-scheduler-scala-example",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.5"
    )
  ).dependsOn(core, `akka-actor`)

val root = (project in file("."))
  .settings(baseSettings)
  .settings(
    name := "chronos-scheduler-scala-root"
  ).aggregate(core, `akka-actor`, `example`)

// --- Custom commands
addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
