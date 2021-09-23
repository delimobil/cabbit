ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "ru.delimobil"
ThisBuild / crossScalaVersions ++= Seq("2.13.6", "3.0.2")

val fs2Version = "2.5.9"
val circeVersion = "0.14.1"
val pureconfigVersion = "0.16.0"

val root = (project in file("."))
  .settings(
    name := "cabbit",
    version := "0.0.16-SNAPSHOT",
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq("-source:3.0-migration")
        case _ =>
          Seq("-deprecation", "-Xfatal-warnings")
      }
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) =>
          List(
            "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0",
          )
        case Some((2, 13)) =>
          List(
            "com.github.pureconfig" %% "pureconfig" % pureconfigVersion % Test,
            "com.github.pureconfig" %% "pureconfig-cats" % pureconfigVersion % Test,
          )
        case _ =>
          Nil
      }
    },
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version % Test,
      "org.scalatest" %% "scalatest" % "3.2.9" % Test,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion % Test,
      "com.rabbitmq" % "amqp-client" % "5.13.1",
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.39.7" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.32" % Test,
    ),
    Test / publishArtifact := true,
    // sonatype config
    publishTo := sonatypePublishToBundle.value,
    ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/delimobil/cabbit")),
    scmInfo := Some(ScmInfo(url("https://github.com/delimobil/cabbit"), "scm:git@github.com/delimobil/cabbit.git")),
    developers := List(
      Developer(
        id = "nikiforo",
        name = "Artem Nikiforov",
        email = "anikiforov@delimobil.ru",
        url = url("https://github.com/nikiforo"),
      )
    )
  )
