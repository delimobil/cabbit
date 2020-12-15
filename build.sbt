ThisBuild / scalaVersion := "2.13.4"
ThisBuild / organization := "ru.delimobil"

lazy val root = (project in file("."))
  .settings(
    name := "cabbit",
    version := "0.0.1",
    addCompilerPlugin("com.olegpy" % "better-monadic-for_2.13" % "0.3.1"),
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "2.4.6",
      "org.scalatest" %% "scalatest" % "3.2.3" % "test",
      "io.circe" %% "circe-core" % "0.13.0",
      "io.circe" %% "circe-parser" % "0.13.0" % "test",
      "com.rabbitmq" % "amqp-client" % "5.10.0",
      "com.dimafeng" %% "testcontainers-scala-rabbitmq"  % "0.38.7" % "test",
      "org.slf4j" % "slf4j-simple" % "1.7.30" % "test",
    )
  )
