ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "ru.delimobil"
ThisBuild / crossScalaVersions ++= Seq("2.13.6", "3.0.1")

val fs2Version = "2.5.6"

val root = (project in file("."))
  .settings(
    name := "cabbit",
    version := "0.0.10-SNAPSHOT",
    scalacOptions ++= {
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq("-source:3.0-migration")
        case _ =>
          Seq(
            "-deprecation",
            "-Xfatal-warnings",
            "-Wunused:imports,privates,locals",
          )
      })
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          List(compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
        case _ =>
          Nil
      }
    },
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version % Test,
      "org.typelevel" %% "cats-effect" % "2.5.3",
      "org.scalatest" %% "scalatest" % "3.2.9" % Test,
      "io.circe" %% "circe-core" % "0.14.1",
      "io.circe" %% "circe-parser" % "0.14.1" % Test,
      "com.rabbitmq" % "amqp-client" % "5.13.0",
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.39.5" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.32" % Test,
    ),
  )
