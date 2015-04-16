package ohnosequences.compota.aws.deployment

trait  AnyMetadata {
  val artifact: String
  val jarUrl: String
}

class Metadata(val artifact: String, val jarUrl: String) extends AnyMetadata
