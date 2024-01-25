addCommandAlias("fmt", "; scalafmtAll ; scalafmtSbt")
addCommandAlias("fmtCheck", "; scalafmtCheckAll ; scalafmtSbtCheck")

name := "B4SMT-project"
scalaVersion := "2.13.12"

val commonSettings = Seq(
  scalaVersion := "2.13.12",
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
  libraryDependencies := Seq("org.chipsalliance" %% "chisel" % "5.1.0"),
  addCompilerPlugin(
    "org.chipsalliance" % "chisel-plugin" % "5.1.0" cross CrossVersion.full,
  ),
  Test / logBuffered := false,
  Test / parallelExecution := false,
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
