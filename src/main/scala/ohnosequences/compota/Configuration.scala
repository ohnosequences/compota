package ohnosequences.compota

import ohnosequences.compota.bundles.NisperonMetadataBuilder

import ohnosequences.awstools.autoscaling._
import ohnosequences.awstools.ec2.{InstanceSpecs, InstanceType}
import ohnosequences.awstools.s3.ObjectAddress

import scala.concurrent.duration.Duration

object CompotaConfiguration {

  val defaultInstanceSpecs = InstanceSpecs(
    instanceType = InstanceType.t1_micro,
    amiId = "",
    securityGroups = List("nispero"),
    keyName = "nispero",
    instanceProfile = Some("compota"),
    deviceMapping = Map("/dev/xvdb" -> "ephemeral0")
  )
}

class CompotaConfiguration(
                                  val metadataBuilder: NisperonMetadataBuilder,
                                  val email: String,
                                  val managerGroupConfiguration: GroupConfiguration = SingleGroup(InstanceType.t1_micro, OnDemand),
                                  val metamanagerGroupConfiguration: GroupConfiguration = SingleGroup(InstanceType.m1_medium, OnDemand),
                                  val timeout: Duration,
                                  val password: String,
                                  val autoTermination: Boolean = true,
                                  val removeAllQueues: Boolean = false,
                                  val errorThreshold: Int = 10,
                                  val workingDir: String = "/media/ephemeral0",
                                  val defaultInstanceSpecs: InstanceSpecs = CompotaConfiguration.defaultInstanceSpecs) {

  def controlTopic: String = Naming.name(this, "controlTopic")

  def artifactAddress = metadataBuilder.jarAddress

  def notificationTopic: String = Naming.notificationTopic(this)

  def id = metadataBuilder.id

  def metamanagerQueue = Naming.name(this, "metamanager")

  def metamanagerGroup = Naming.name(this, "metamanager")

  def bucket = Naming.s3name(id)

  def results = ObjectAddress(bucket, "results")

  def deadLettersQueue = Naming.name(this, "deadletters")

  def errorTable = Naming.name(this, "errors")

  def loggingDestination(id: String): ObjectAddress = ObjectAddress(bucket, "logs/id")

}


abstract class GroupConfiguration {
  val instanceType: InstanceType
  val min: Int
  val max: Int
  val size: Int
  val purchaseModel: PurchaseModel


  def autoScalingGroup(name: String, defaultInstanceSpecs: InstanceSpecs, amiId: String, userData: String): AutoScalingGroup = {
    val launchingConfiguration = LaunchConfiguration(
      name = name,
      purchaseModel = purchaseModel,
      instanceSpecs = defaultInstanceSpecs.copy(
        instanceType = instanceType,
        amiId = amiId,
        userData = userData
      )
    )

    AutoScalingGroup(
      name = name,
      launchingConfiguration = launchingConfiguration,
      minSize = min,
      maxSize = max,
      desiredCapacity = size
    )

  }
}

case class SingleGroup(
                        instanceType: InstanceType = InstanceType.t1_micro,
                        purchaseModel: PurchaseModel = OnDemand
                        ) extends GroupConfiguration {
  val min = 1
  val max = 1
  val size = 1
}

case class Group(
                  instanceType: InstanceType = InstanceType.t1_micro,
                  min: Int = 0,
                  max: Int = 10,
                  size: Int,
                  purchaseModel: PurchaseModel = OnDemand
                  ) extends GroupConfiguration


case class NisperoConfiguration(nisperonConfiguration: CompotaConfiguration, name: String, workerGroup: GroupConfiguration = SingleGroup()) {

  def workersGroupName = Naming.name(nisperonConfiguration, this, "worker")

  def managerGroupName = Naming.name(nisperonConfiguration, this, "manager")

  def controlQueueName = Naming.name(nisperonConfiguration, this, "control")

}


