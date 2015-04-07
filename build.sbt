Nice.scalaProject

name := "compota"
description := "compota (ex nisperon)"
organization := "ohnosequences"

bucketSuffix := "era7.com"



libraryDependencies ++= Seq(
  "commons-io"     % "commons-io" % "2.1",
  "org.json4s"    %% "json4s-native" % "3.2.11",
  "ohnosequences" %% "aws-scala-tools" % "0.13.0-SNAPSHOT",
  "com.novocode" % "junit-interface" % "0.11" % "test"
//  "net.databinder" %% "unfiltered-filter" % "0.7.1",
//  "net.databinder" %% "unfiltered-netty" % "0.7.1",
//  "net.databinder" %% "unfiltered-netty-server" % "0.7.1",

)

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.0" % "test"

dependencyOverrides += "commons-codec" % "commons-codec" % "1.6"

dependencyOverrides += "org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.2"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "40", "-minSuccessfulTests", "10", "-workers", "1", "-verbosity", "1")


