package ohnosequences.compota.aws

import ohnosequences.awstools.AWSClients

object TestCredentials {
  val aws: Option[AWSClients] = {
    generated.test.credentials.credentialsProvider.map (AWSClients.create(_))
  }
}
