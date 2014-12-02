Nice.scalaProject

name := "compota"
description := "compota (ex nisperon)"
organization := "ohnosequences"

bucketSuffix := "era7.com"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "commons-io"     % "commons-io" % "2.1",
  "org.json4s"    %% "json4s-native" % "3.2.5",
  "ohnosequences" %% "aws-scala-tools" % "0.9.2-SNAPSHOT"
//  "net.databinder" %% "unfiltered-filter" % "0.7.1",
//  "net.databinder" %% "unfiltered-netty" % "0.7.1",
//  "net.databinder" %% "unfiltered-netty-server" % "0.7.1",
//  "com.novocode" % "junit-interface" % "0.10" % "test"
)

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.0" % "test"

dependencyOverrides += "commons-codec" % "commons-codec" % "1.6"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "40", "-minSuccessfulTests", "10", "-workers", "1", "-verbosity", "1")


