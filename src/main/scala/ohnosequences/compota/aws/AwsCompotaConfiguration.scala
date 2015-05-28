package ohnosequences.compota.aws

import java.io.File

import ohnosequences.awstools.autoscaling._
import ohnosequences.awstools.ec2.{InstanceType, InstanceSpecs}
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.{AnyNisperoConfiguration, AnyCompotaConfiguration}
import ohnosequences.compota.aws.deployment.{AnyMetadata, Metadata, userScriptGenerator}



trait AwsCompotaConfiguration extends AnyCompotaConfiguration {

  def metadata: AnyMetadata

  def localErrorThreshold: Int = 100

  def globalErrorThresholdPerNameSpace: Int = 10

  def name = metadata.artifact

  def loggingDebug: Boolean = false

  def amiId = "ami-5256b825"

  def securityGroups = List("compota")

  def keyName = "compota"

  def instanceProfile: Option[String] = Some("compota")

  def deviceMapping = Map("/dev/xvdb" -> "ephemeral0")

  def workingDirectory = new File("/media/ephemeral0/compota")

  def loggingDirectory =  new File(workingDirectory, "logs")

  def managerInstanceType = InstanceType.c1_medium

  def errorTable: String = Resources.errorTable(metadata)

  def loggerBucket: String = Resources.loggerBucket(metadata)

  def controlQueue: String = Resources.controlQueue(metadata)

  def managerInstanceSpecs = new InstanceSpecs(
    instanceType = managerInstanceType,
    instanceProfile = instanceProfile,
    deviceMapping = deviceMapping,
    amiId = amiId,
    userData = userScriptGenerator.generate("manager", "manager", metadata.jarUrl, metadata.testJarUrl, metadata.mainClass, workingDirectory.getAbsolutePath)
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


abstract class AwsNisperoConfiguration extends AnyNisperoConfiguration {

  def name: String

  def compotaConfiguration: AwsCompotaConfiguration

  def workerInstanceType = compotaConfiguration.workerInstanceType

  def workerWorkingDirectory = compotaConfiguration.workingDirectory

  def workerPurchaseMethod = compotaConfiguration.workerPurchaseModel

  def workerInstanceSpecs = new InstanceSpecs(
    instanceType = workerInstanceType,
    instanceProfile = compotaConfiguration.instanceProfile,
    deviceMapping = compotaConfiguration.deviceMapping,
    amiId = compotaConfiguration.amiId,
    userData = userScriptGenerator.generate(name, "worker", compotaConfiguration.metadata.jarUrl, compotaConfiguration.metadata.testJarUrl, compotaConfiguration.metadata.mainClass, workerWorkingDirectory.getAbsolutePath)
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

