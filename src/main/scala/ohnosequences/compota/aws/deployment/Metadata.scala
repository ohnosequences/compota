package ohnosequences.compota.aws.deployment

trait  AnyMetadata {
  val artifact: String
  val jarUrl: String
  val testJarUrl: Option[String]
  val mainClass: Option[String]
}

case class Metadata(artifact: String,
               jarUrl: String,
               testJarUrl: Option[String],
               mainClass: Option[String]) extends AnyMetadata
