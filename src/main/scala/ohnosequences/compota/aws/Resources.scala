package ohnosequences.compota.aws

import ohnosequences.compota.aws.deployment.Metadata

object Resources {

  def managerLaunchConfiguration(metadata: Metadata) = "compota." + metadata.artifact + ".manager"

  def managerAutoScalingGroup(metadata: Metadata) = "compota." + metadata.artifact + ".manager"

  def workerLaunchConfiguration(metadata: Metadata, name: String) = "compota." + metadata.artifact + "." + name + "." + "worker"
  def workerAutoScalingGroup(metadata: Metadata, name: String) = "compota." + metadata.artifact + "." + name + "." + "worker"


  def dynamodbTable(metadata: Metadata, name: String) = "compota." + metadata.artifact + "." + name

  def sqsQueue(metadata: Metadata, name: String) = "compota." + metadata.artifact + "." + name

}
