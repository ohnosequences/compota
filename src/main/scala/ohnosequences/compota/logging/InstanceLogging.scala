package ohnosequences.compota.logging

import ohnosequences.compota.{CompotaConfiguration, AWS}
import ohnosequences.awstools.s3.ObjectAddress
import java.io.File

//todo fix location
//todo split big log into chunks ...
object InstanceLogging {



  def putLog(aws: AWS, nisperonConfiguration: CompotaConfiguration, instanceId: String, timeout: Int = 5 ) {
    Thread.sleep(timeout * 1000)
  //  val instanceId = aws.ec2.getCurrentInstanceId.getOrElse("undefined_" + System.currentTimeMillis())
    val logAddress = getLocation(nisperonConfiguration, instanceId)
    //todo incorporate with ami
    aws.s3.putObject(logAddress, new File("/root/log.txt"))
  }

  def getLocation(nisperonConfiguration: CompotaConfiguration, instance: String) = ObjectAddress(nisperonConfiguration.bucket, instance)

}
