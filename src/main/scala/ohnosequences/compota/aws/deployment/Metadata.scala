package ohnosequences.compota.aws.deployment

trait  AnyMetadata {
  val artifact: String
  val jarUrl: String
  val testJarUrl: Option[String]
  val mainClass: Option[String]
}

class Metadata(val artifact: String,
               val jarUrl: String,
               val testJarUrl: Option[String],
               val mainClass: Option[String]) extends AnyMetadata
