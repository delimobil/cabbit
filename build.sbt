ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "ru.delimobil"

lazy val root = (project in file("."))
  .settings(
    name := "cabbit",
    version := "0.0.7-SNAPSHOT",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "2.5.6",
      "org.scalatest" %% "scalatest" % "3.2.9" % Test,
      "io.circe" %% "circe-core" % "0.13.0",
      "io.circe" %% "circe-parser" % "0.13.0" % Test,
      "com.rabbitmq" % "amqp-client" % "5.12.0",
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.39.4" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.30" % Test
    )
  )
