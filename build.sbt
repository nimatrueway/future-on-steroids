lazy val `future-on-steroids` = (project in file("."))
  .settings(
    organization := "io.github.nimatrueway",
    name := "future-on-steroids",
    version := "0.0.1"
    scalaVersion := "2.13.12"
    crossScalaVersions := Seq("2.12.18", "2.13.12", "3.3.1"),
    libraryDependencies ++= Seq(
      "com.softwaremill.retry" %% "retry" % "0.3.6", // for retry
      "com.softwaremill.odelay" %% "odelay-core" % "0.4.0", // for scheduling
      "org.scalameta" %% "munit" % "1.0.0-M10",
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
