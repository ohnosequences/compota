package ohnosequences.compota.aws

import ohnosequences.awstools.AWSClients

object TestCredentials {
  val aws: Option[AWSClients] = {
    ohnosequences.compota.test.generated.credentials.credentialsProvider.map (AWSClients.create(_))
  }
}
