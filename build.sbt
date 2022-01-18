ThisBuild / scalaVersion := "2.13.7"
ThisBuild / organization := "ru.delimobil"
ThisBuild / crossScalaVersions ++= Seq("2.12.15", "2.13.7", "3.1.0")

val catsVersion = "2.6.1"
val fs2VersionCE2 = "3.2.4"
val fs2VersionCE3 = "3.2.2"
val circeVersion = "0.14.1"
val pureconfigVersion = "0.16.0"
val amqpClientVersion = "5.13.1"

val publishSettings = Seq(
  // sonatype config
  publishTo := sonatypePublishToBundle.value,
  ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/delimobil/cabbit")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/delimobil/cabbit"), "scm:git@github.com/delimobil/cabbit.git")
  ),
  developers := List(
    Developer(
      id = "nikiforo",
      name = "Artem Nikiforov",
      email = "anikiforov@delimobil.ru",
      url = url("https://github.com/nikiforo")
    )
  )
)

val commonSettings = Seq(
  version := "0.1.2",
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) =>
        Seq("-source:3.0-migration")
      case Some((2, 13)) =>
        Seq("-deprecation", "-Xfatal-warnings")
      case _ =>
        Seq()
    }
  }
)

val core = (project in file("core"))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "cabbit-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "com.rabbitmq" % "amqp-client" % amqpClientVersion
    )
  )

val root = (project in file("."))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "cabbit_ce2",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          List(
            "com.github.pureconfig" %% "pureconfig" % pureconfigVersion % Test,
            "com.github.pureconfig" %% "pureconfig-cats" % pureconfigVersion % Test
          )
        case _ =>
          Nil
      }
    },
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.10" % Test,
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.39.12" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.32" % Test
    ),
    libraryDependencies += "co.fs2" %% "fs2-core" % fs2VersionCE2,
    Test / publishArtifact := true
  )

val ce3 = (project in file("ce3"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "cabbit",
    libraryDependencies += "co.fs2" %% "fs2-core" % fs2VersionCE3
  )

val circe = (project in file("circe"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "cabbit-circe",
    libraryDependencies += "io.circe" %% "circe-core" % circeVersion
  )
