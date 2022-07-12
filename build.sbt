ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(name := "B4-processor")

scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-P:chiselplugin:genBundleElements"
)

Test / logBuffered := true
Test / parallelExecution := false
Test / testOptions += Tests.Argument(
  TestFrameworks.ScalaTest,
  "-u",
  "target/test-reports"
)

scalaVersion := "2.13.8"

addCompilerPlugin(
  "edu.berkeley.cs" % "chisel3-plugin" % "3.5.3" cross CrossVersion.full
)
libraryDependencies ++= Seq(
  "edu.berkeley.cs" %% "chisel3" % "3.5.3",
  // We also recommend using chiseltest for writing unit tests
  "edu.berkeley.cs" %% "chiseltest" % "0.5.3" % "test"
)

// for test output in html
projectDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.12" % Test,
  "com.vladsch.flexmark" % "flexmark" % "0.64.0" % Test,
  "com.vladsch.flexmark" % "flexmark-profile-pegdown" % "0.64.0" % Test
)
