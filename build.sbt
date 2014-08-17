Nice.scalaProject

name := "compota"

description := "compota (ex nisperon)"

organization := "ohnosequences"

isPrivate := false

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "commons-io"     % "commons-io" % "2.1",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.json4s"    %% "json4s-native" % "3.2.10",
  "ohnosequences" %% "aws-scala-tools" % "0.7.1-SNAPSHOT",
  "com.amazonaws" % "aws-java-sdk" % "1.8.0",
  "ohnosequences" %% "statika" % "1.1.0-SNAPSHOT",
  "ohnosequences" %% "aws-statika" % "1.1.0-SNAPSHOT",
  "ohnosequences" %% "amazon-linux-ami" % "0.14.1-SNAPSHOT",
  "net.databinder" %% "unfiltered-filter" % "0.7.1",
  "net.databinder" %% "unfiltered-netty" % "0.7.1",
  "net.databinder" %% "unfiltered-netty-server" % "0.7.1",
  "com.novocode"   % "junit-interface" % "0.10" % "test"
)

resolvers ++= Seq(
  "Era7 Releases"       at "http://releases.era7.com.s3.amazonaws.com",
  "Era7 Snapshots"      at "http://snapshots.era7.com.s3.amazonaws.com",
  "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
)

resolvers += Resolver.url("Statika public ivy releases",  url("http://releases.statika.ohnosequences.com.s3.amazonaws.com/"))(ivy)

resolvers +=  Resolver.url("era7" + " public ivy releases",  url("http://releases.era7.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)

resolvers +=  Resolver.url("era7" + " public ivy snapshots",  url("http://snapshots.era7.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)


dependencyOverrides += "ohnosequences" %% "aws-scala-tools" % "0.7.1-SNAPSHOT"

dependencyOverrides += "ohnosequences" %% "aws-statika" % "1.1.0-SNAPSHOT"

dependencyOverrides += "ohnosequences" % "amazon-linux-ami_2.11" % "0.14.1-SNAPSHOT"

dependencyOverrides += "commons-codec" % "commons-codec" % "1.6"

dependencyOverrides += "org.scala-lang" % "scala-library" % "2.11.1"

dependencyOverrides += "org.scala-lang" % "scala-compiler" % "2.11.1"

dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.0.2"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.1.2"

dependencyOverrides += "com.amazonaws" % "aws-java-sdk" % "1.8.0"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.1.2"

dependencyOverrides += "jline" % "jline" % "2.6"

dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.5"

//test 5

