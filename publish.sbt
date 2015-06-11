bucketSuffix := "era7.com"

publishArtifact in (Test, packageBin) := true

// fat jar merge conflict settings
mergeStrategy in assembly <<= (mergeStrategy in assembly) {

  (old) => {
    case "log4j.properties" => MergeStrategy.first
    case "overview.html" => MergeStrategy.first
    case PathList("org", "apache", "commons", _*) => MergeStrategy.first
    case x => old(x)
  }
}
