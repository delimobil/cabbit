ThisBuild / organization := "ru.delimobil"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / crossScalaVersions ++= Seq("2.12.17", "3.2.1")

val kindProjectorVersion = "0.13.3"
val catsVersion = "2.6.1"
val fs2VersionCE2 = "2.5.11"
val fs2VersionCE3 = "3.2.14"
val circeVersion = "0.14.1"
val amqpClientVersion = "5.13.1"

val pureconfigVersion = "0.17.1"
val scalatestVersion = "3.2.11"
val testContainersVersion = "0.40.2"
val slf4jVersion = "1.7.36"

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
  version := "0.2.0-RC3",
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) =>
        Seq("-source:3.0-migration", "-Ykind-projector:underscores")
      case Some((2, 13)) =>
        Seq("-deprecation", "-Xfatal-warnings")
      case Some((2, 12)) =>
        Seq("-Ypartial-unification")
      case _ =>
        Seq()
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          compilerPlugin(
            ("org.typelevel" %% "kind-projector" % kindProjectorVersion).cross(CrossVersion.full)
          )
        )
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
    libraryDependencies += "co.fs2" %% "fs2-core" % fs2VersionCE2
  )

val ce3 = (project in file("ce3"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "cabbit",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % testContainersVersion % Test,
      "org.slf4j" % "slf4j-simple" % slf4jVersion % Test
    ),
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
      "co.fs2" %% "fs2-core" % fs2VersionCE3,
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % testContainersVersion % Test
    ),
    Test / publishArtifact := true
  )

val circe = (project in file("circe"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "cabbit-circe",
    libraryDependencies += "io.circe" %% "circe-core" % circeVersion
  )
