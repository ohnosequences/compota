Nice.scalaProject

name          := "compota"
description   := "compota (ex nisperon)"
organization  := "ohnosequences"

resolvers := Seq[Resolver](
  organization.value + " public maven releases"  at s3https(bucketRegion.value, "releases." + bucketSuffix.value),
  organization.value + " public maven snapshots" at s3https(bucketRegion.value, "snapshots." + bucketSuffix.value),
  Resolver.url(organization.value + " public ivy releases", url(s3https(bucketRegion.value, "releases." + bucketSuffix.value)))(ivy),
  Resolver.url(organization.value + " public ivy snapshots", url(s3https(bucketRegion.value, "snapshots." + bucketSuffix.value)))(ivy)
) ++ resolvers.value


libraryDependencies ++= Seq(
  "commons-io"      %  "commons-io"               % "2.4",
  "org.json4s"      %% "json4s-native"            % "3.2.11",
  "ohnosequences"   %% "aws-scala-tools"          % "0.13.2-SNAPSHOT",
  "net.databinder"  %% "unfiltered-filter"        % "0.8.4",
  "net.databinder"  %% "unfiltered-netty"         % "0.8.4",
  "net.databinder"  %% "unfiltered-netty-server"  % "0.8.4"
)

// test dependencies
libraryDependencies ++= Seq(
  "com.novocode"    %   "junit-interface"   % "0.11"    % "test",
  "org.scalacheck"  %%  "scalacheck"        % "1.11.0"  % "test"
)

// dependencyOverrides
dependencyOverrides ++= Set(
  "commons-codec"           %   "commons-codec"             % "1.6",
  "org.scala-lang.modules"  %%  "scala-parser-combinators"  % "1.0.3",
  "org.scala-lang.modules"  %%  "scala-xml"                 % "1.0.3"
)

testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "40", "-minSuccessfulTests", "10", "-workers", "1", "-verbosity", "1")
