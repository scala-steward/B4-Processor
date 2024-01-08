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
  libraryDependencies := Seq("org.chipsalliance" %% "chisel" % "5.1.0"),
  addCompilerPlugin(
    "org.chipsalliance" % "chisel-plugin" % "5.1.0" cross CrossVersion.full,
  ),
)

lazy val pextExecutor = (project in file("pext"))
  .settings(commonSettings, name := "B4SMT-PExtentiosnExecutor")
  .dependsOn(chiselFormal)

lazy val chiselFormal = (project in file("chisel-formal"))
  .settings(
    commonSettings,
    name := "B4SMT-ChiselFormal",
    libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "5.0.2",
  )

lazy val b4smt = (project in file("b4smt"))
  .settings(
    commonSettings,
    name := "B4SMT",
    libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "5.0.2" % "test",
  )
  .dependsOn(pextExecutor, chiselFormal)

Test / logBuffered := false
Test / parallelExecution := false
