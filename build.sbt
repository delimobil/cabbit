ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "ru.delimobil"
ThisBuild / crossScalaVersions ++= Seq("2.12.15", "2.13.6", "3.0.2")

val catsVersion = "2.6.1"
val fs2VersionCE2 = "2.5.9"
val fs2VersionCE3 = "3.1.3"
val circeVersion = "0.14.1"
val pureconfigVersion = "0.16.0"

val shared = (project in file("shared"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "com.rabbitmq" % "amqp-client" % "5.13.1",
      "io.circe" %% "circe-core" % circeVersion,
    )
  )

val commonSettings = Seq(
  version := "0.0.17",
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) =>
        Seq("-source:3.0-migration")
      case Some((2,13)) =>
        Seq("-deprecation", "-Xfatal-warnings", "-target:jvm-1.8")
      case _ =>
        Seq("-target:jvm-1.8")
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        List(
          "com.github.pureconfig" %% "pureconfig" % pureconfigVersion % Test,
          "com.github.pureconfig" %% "pureconfig-cats" % pureconfigVersion % Test,
          compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
        )
      case _ =>
        Nil
    }
  },
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.10" % Test,
    "io.circe" %% "circe-parser" % circeVersion % Test,
    "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.39.8" % Test,
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

val root = (project in file("."))
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(
    name := "cabbit_ce2",
    libraryDependencies += "co.fs2" %% "fs2-core" % fs2VersionCE2,
  )

val ce3 = (project in file("ce3"))
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(
    name := "cabbit",
    libraryDependencies += "co.fs2" %% "fs2-core" % fs2VersionCE3,
  )
