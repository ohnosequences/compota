package ohnosequences.compota.aws

import ohnosequences.awstools.autoscaling._
import ohnosequences.awstools.ec2.{InstanceType, InstanceSpecs}
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.aws.deployment.{Metadata, userScriptGenerator}



abstract class AwsCompotaConfigurationAux(val metadata: Metadata) {

  def name = metadata.artifact

  def amiId = "ami-5256b825"

  def securityGroups = List("compota")

  def keyName = "compota"

  def instanceProfile: Option[String] = Some("compota")

  def deviceMapping = Map("/dev/xvdb" -> "ephemeral0")

  def workingDirectory = "/media"


  def managerInstanceType = InstanceType.c1_medium


  def managerInstanceSpecs = new InstanceSpecs(
    instanceType = managerInstanceType,
    instanceProfile = instanceProfile,
    deviceMapping = deviceMapping,
    amiId = amiId,
    userData = userScriptGenerator.generate("manager", "manager", metadata.jarUrl, workingDirectory)
  )

  def managerPurchaseModel: PurchaseModel = OnDemand

  def managerLaunchConfiguration = LaunchConfiguration(
    name = Resources.managerLaunchConfiguration(metadata),
    instanceSpecs = managerInstanceSpecs,
    purchaseModel = managerPurchaseModel
  )

  def managerMinSize: Int = 0
  def managerDesiredSize: Int = 1
  def managerMaxSize: Int = 1

  def managerAutoScalingGroup = AutoScalingGroup(
    name = Resources.managerAutoScalingGroup(metadata),
    minSize = managerMinSize,
    desiredCapacity = managerDesiredSize,
    maxSize = managerMaxSize,
    launchingConfiguration = managerLaunchConfiguration
  )

  def workerInstanceType: InstanceType = InstanceType.m1_medium

  def workerPurchaseModel: PurchaseModel = SpotAuto


}

//class CompotaConfiguration(val name: String) extends CompotaConfigurationAux


abstract class AwsNisperoConfigurationAux(val name: String, val compotaConfiguration: AwsCompotaConfigurationAux) {

  def workerInstanceType = compotaConfiguration.workerInstanceType

  def workerWorkingDirectory = compotaConfiguration.workingDirectory

  def workerPurchaseMethod = compotaConfiguration.workerPurchaseModel

  def workerInstanceSpecs = new InstanceSpecs(
    instanceType = workerInstanceType,
    instanceProfile = compotaConfiguration.instanceProfile,
    deviceMapping = compotaConfiguration.deviceMapping,
    amiId = compotaConfiguration.amiId,
    userData = userScriptGenerator.generate(name, "worker", compotaConfiguration.metadata.jarUrl, workerWorkingDirectory)
  )

  def workerLaunchConfiguration = LaunchConfiguration(
    name = Resources.workerLaunchConfiguration(compotaConfiguration.metadata, name),
    instanceSpecs = workerInstanceSpecs,
    purchaseModel = workerPurchaseMethod
  )

  def workerMinSize: Int = 0
  def workerDesiredSize: Int = 1
  def workerMaxSize: Int = 100

  def workerAutoScalingGroup = AutoScalingGroup(
    name = Resources.workerAutoScalingGroup(compotaConfiguration.metadata, name),
    minSize = workerMinSize,
    desiredCapacity = workerDesiredSize,
    maxSize = workerMaxSize,
    launchingConfiguration = workerLaunchConfiguration
  )



}

//case class CompotaConfiguration (
//  name: String,
//  managerGroupLaunchConfiguration: LaunchConfiguration = LaunchConfiguration(
//    name = name,
//    purchaseModel = OnDemand,
//    instanceSpecs = new InstanceSpecs(
//      instanceType = InstanceType.c1_medium,
//      instanceAm
//
//    )
//  )
//}

//trait NisperoConfiguration {
//  val compotaConfiguration: CompotaConfiguration
//
//
//}