package ohnosequences.compota.aws

import ohnosequences.awstools.autoscaling.{OnDemand, LaunchConfiguration, AutoScalingGroup, PurchaseModel}
import ohnosequences.awstools.ec2.{InstanceSpecs, InstanceType}


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
                        instanceType: InstanceType,
                        purchaseModel: PurchaseModel = OnDemand
                        ) extends GroupConfiguration {
  val min = 0
  val max = 1
  val size = 1
}

case class Group(
                  instanceType: InstanceType,
                  min: Int = 0,
                  max: Int = 100,
                  size: Int,
                  purchaseModel: PurchaseModel = OnDemand
                  ) extends GroupConfiguration

