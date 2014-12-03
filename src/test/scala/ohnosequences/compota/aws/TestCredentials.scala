package ohnosequences.compota.aws

object TestCredentials {
  val aws: Option[AWSClients] = {
    generated.test.credentials.credentialsProvider.map (AWSClients.create(_))
  }
}
