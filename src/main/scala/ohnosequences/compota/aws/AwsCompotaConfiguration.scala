package ohnosequences.compota.aws

import java.io.File

import com.amazonaws.auth.{InstanceProfileCredentialsProvider, AWSCredentialsProvider}
import ohnosequences.awstools.autoscaling._
import ohnosequences.awstools.dynamodb.RepeatConfiguration
import ohnosequences.awstools.ec2.{InstanceType, InstanceSpecs}
import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.queues.AnyQueue
import ohnosequences.compota.{Namespace, AnyNisperoConfiguration, AnyCompotaConfiguration}
import ohnosequences.compota.aws.deployment.{AnyMetadata, Metadata, userScriptGenerator}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration


trait AwsCompotaConfiguration extends AnyCompotaConfiguration {

  def awsRegion: Region = Region.Ireland

  def localAwsCredentialsProvider: AWSCredentialsProvider

  def instanceAwsCredentialsProvider: AWSCredentialsProvider = new InstanceProfileCredentialsProvider()

  def notificationTopic: String = {
    Resources.notificationTopic(notificationEmail)
  }

  def controlQueueVisibilityTimeout: Duration = Duration(10, MINUTES)

  def notificationEmail: String

  def metadata: AnyMetadata

  def localErrorThreshold: Int = 1000

  override def consoleHTTPS: Boolean = false

  override def consolePort: Int = 80

  override def environmentRepeatConfiguration: RepeatConfiguration = RepeatConfiguration(
    attemptThreshold = 100,
    initialTimeout = Duration(1, SECONDS),
    timeoutThreshold = Duration(1, MINUTES),
    coefficient = 1.2
  )

  override def globalErrorThreshold: Int = 10

  def name = metadata.artifact

  def amiId = "ami-a10897d6" //"ami-5256b825"

  def securityGroups = List("compota")

  def keyName: String

  def instanceProfile: Option[String] = Some("compota")

  def deviceMapping = Map("/dev/xvdb" -> "ephemeral0")

  def workingDirectory = new File("/media/ephemeral0/compota")

  def loggingDirectory =  new File(workingDirectory, "logs")

  def managerInstanceType = InstanceType.c3_large

  def errorTable: String = Resources.errorTable(metadata)

  def resultsBucket: String = Resources.compotaBucket(metadata)

  def loggerBucket: String = Resources.compotaBucket(metadata)


  def logUploaderTimeout: Duration = Duration(1, MINUTES)

  def controlQueue: String = Resources.controlQueue(metadata)

  def managerInstanceSpecs = new InstanceSpecs(
    instanceType = managerInstanceType,
    instanceProfile = instanceProfile,
    keyName = keyName,
    securityGroups = securityGroups,
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

  def workerInstanceType: InstanceType = InstanceType.m3_medium

  def workerPurchaseModel: PurchaseModel = SpotAuto




  def loggingDestination(instanceId: InstanceId, namespace: Namespace): Option[ObjectAddress] = (instanceId, namespace) match {
    case (id, Namespace.root) => {
      //instance log
      Some(ObjectAddress(loggerBucket, id.id))
    }
    case (id, Namespace(Namespace.worker :: taskId :: Nil)) => {
      //task log
      Some(ObjectAddress(loggerBucket, taskId) / id.id)
    }
    case _ => None
  }

  def resultsDestination[Q <: AnyQueue](queue: Q): Option[ObjectAddress] = {
    Some(ObjectAddress(resultsBucket, "results") / queue.name)
  }
}

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
    keyName = compotaConfiguration.keyName,
    securityGroups = compotaConfiguration.securityGroups,
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




  override def workers: Int = workerDesiredSize

  def workerAutoScalingGroup = AutoScalingGroup(
    name = Resources.workerAutoScalingGroup(compotaConfiguration.metadata, name),
    minSize = workerMinSize,
    desiredCapacity = workerDesiredSize,
    maxSize = workerMaxSize,
    launchingConfiguration = workerLaunchConfiguration
  )



}

