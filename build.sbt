addCommandAlias("fmt", "; scalafmtAll ; scalafmtSbt")
addCommandAlias("fmtCheck", "; scalafmtCheckAll ; scalafmtSbtCheck")

ThisBuild / version := "0.1.0-SNAPSHOT"

val commonSettings = Seq(
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:reflectiveCalls",
    "-Ymacro-annotations",
    "-JXss512m",
    "-JXmx2G",
  ),
  scalaVersion := "2.13.12",
  libraryDependencies := Seq(
    "org.chipsalliance" %% "chisel" % "5.1.0",
    "edu.berkeley.cs" %% "chiseltest" % "5.0.2" % "test",
  ),
  addCompilerPlugin(
    "org.chipsalliance" % "chisel-plugin" % "5.1.0" cross CrossVersion.full,
  ),
)

lazy val pextExecutor = (project in file("pext"))
  .settings(commonSettings, name := "B4SMT-PExtentiosnExecutor")

lazy val b4smt = (project in file("b4smt"))
  .settings(commonSettings, name := "B4SMT")
  .dependsOn(pextExecutor)

Test / logBuffered := false
Test / parallelExecution := false
