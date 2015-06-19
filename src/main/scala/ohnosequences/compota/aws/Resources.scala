package ohnosequences.compota.aws

import ohnosequences.compota.aws.deployment.AnyMetadata

object Resources {

  def managerLaunchConfiguration(metadata: AnyMetadata) = "compota_" + metadata.artifact + "_manager"

  def managerAutoScalingGroup(metadata: AnyMetadata) = "compota_" + metadata.artifact + "_manager"

  def workerLaunchConfiguration(metadata: AnyMetadata, name: String) = "compota_" + metadata.artifact + "_" + name + "_" + "worker"

  def workerAutoScalingGroup(metadata: AnyMetadata, name: String) = "compota_" + metadata.artifact + "_" + name + "_" + "worker"

  def errorTable(metadata: AnyMetadata): String = dynamodbTable(metadata, "errors")

  //def loggerBucket(metadata: AnyMetadata): String = "compota-" + metadata.artifact + "-logs"

  def compotaBucket(metadata: AnyMetadata): String = "compota-" + metadata.artifact.replace("_", "-")

  def controlQueue(metadata: AnyMetadata): String = dynamodbTable(metadata, "controlQueue")

  def dynamodbTable(metadata: AnyMetadata, name: String) = "compota_" + metadata.artifact + "_" + name

  def sqsQueue(metadata: AnyMetadata, name: String) = "compota_" + metadata.artifact + "_" + name

  def notificationTopic(email: String): String = {
    "compota_" + email.hashCode
  }

}
