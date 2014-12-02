package ohnosequences.compota.aws

import ohnosequences.compota.aws.deployment.Metadata

object Resources {

  def managerLaunchConfiguration(metadata: Metadata) = "compota_" + metadata.artifact + "_manager"

  def managerAutoScalingGroup(metadata: Metadata) = "compota_" + metadata.artifact + "_manager"

  def workerLaunchConfiguration(metadata: Metadata, name: String) = "compota_" + metadata.artifact + "." + name + "_" + "worker"
  def workerAutoScalingGroup(metadata: Metadata, name: String) = "compota_" + metadata.artifact + "." + name + "_" + "worker"


  def dynamodbTable(metadata: Metadata, name: String) = "compota_" + metadata.artifact + "_" + name

  def sqsQueue(metadata: Metadata, name: String) = "compota_" + metadata.artifact + "_" + name

}
