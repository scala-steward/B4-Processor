ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(name := "B4-processor")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
  "-Ymacro-annotations"
)

//Test / logBuffered := false
//Test / parallelExecution := false

scalaVersion := "2.13.11"

val chiselVersion = "5.0.0"

addCompilerPlugin(
  "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
)
libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % chiselVersion,
  "edu.berkeley.cs" %% "chiseltest" % chiselVersion % "test"
)
