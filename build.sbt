ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "ru.delimobil"

val fs2Version = "2.5.9"

val root = (project in file("."))
  .settings(
    name := "cabbit",
    version := "0.0.13",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version % Test,
      "org.scalatest" %% "scalatest" % "3.2.9" % Test,
      "io.circe" %% "circe-core" % "0.13.0",
      "io.circe" %% "circe-parser" % "0.13.0" % Test,
      "com.rabbitmq" % "amqp-client" % "5.12.0",
      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.39.4" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.30" % Test
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
      Developer(id="nikiforo", name="Artem Nikiforov", email="anikiforov@delimobil.ru", url=url("https://github.com/nikiforo")),
    )
  )
