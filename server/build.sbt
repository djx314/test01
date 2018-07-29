import sbt._
import sbt.Keys._

transitiveClassifiers in ThisBuild := Seq("sources", "jar", "javadoc")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"   % "10.1.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.12"
)

scalaVersion := "2.12.6"