import sbt._
import sbt.Keys._

object MyBuild extends Build {
  val geotoolsVersion = "2.7.4"

  lazy val project = Project("root", file(".")) settings(
    //organization := "org.sample.demo",

    name := "carbon",

    scalaVersion := "2.9.2",

    scalacOptions ++= Seq("-deprecation", "-unchecked", "-optimize"),

    parallelExecution := false,

    libraryDependencies ++= Seq(
      "com.google.code.caliper" % "caliper" % "1.0-SNAPSHOT" from "http://n0d.es/jars/caliper-1.0-SNAPSHOT.jar",
      "javax.media" % "jai_core" % "1.1.3" from "http://n0d.es/jars/jai_core-1.1.3.jar",
      "javax.media" % "jai_codec" % "1.1.3" from "http://n0d.es/jars/jai_codec-1.1.3.jar",
      "javax.media" % "jai_imageio" % "1.1" from "http://n0d.es/jars/jai_imageio-1.1.jar",
      "org.scalatest" %% "scalatest" % "1.6.1" % "test",
      "junit" % "junit" % "4.5" % "test",
      "org.eclipse.jetty" % "jetty-webapp" % "8.1.0.RC4",
      "com.sun.jersey" % "jersey-bundle" % "1.11",
      "com.azavea.geotrellis" %% "geotrellis" % "0.8.0-M4d-SNAPSHOT" exclude ("com.typesafe.sbteclipse", "sbteclipse-plugin"),
      "com.codahale" % "jerkson_2.9.1" % "0.5.0"
    ),

    resolvers ++= Seq(
      "local" at "http://192.168.16.41/jars/",
      "NL4J Repository" at "http://nativelibs4java.sourceforge.net/maven/",
      "maven2 dev repository" at "http://download.java.net/maven/2",
      "Scala Test" at "http://www.scala-tools.org/repo-reloases/",
      "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
      "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      "sonatypeSnapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
      "repo.codahale.com" at "http://repo.codahale.com"
    ),

    // enable forking in run
    fork in run := true
  )
}
