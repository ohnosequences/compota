package ohnosequences.compota.aws

import ohnosequences.awstools.autoscaling.AutoScalingGroup
import ohnosequences.awstools.utils.AutoScalingUtils
import ohnosequences.compota.Namespace
import ohnosequences.compota.console.{AnyInstanceInfo, Console}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.QueueChecker
import ohnosequences.compota.queues.AnyQueueOp

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

import java.net.URL



class AwsConsole[N <: AnyAwsNispero](awsCompota: AnyAwsCompota.ofN[N],
                                         env: AwsEnvironment,
                                         controlQueueOp: AnyQueueOp,
                                         queueChecker: QueueChecker[AwsEnvironment]) extends
Console[AwsEnvironment, N, AnyAwsCompota.ofN[N]](awsCompota, env, controlQueueOp, queueChecker) {

  import ohnosequences.compota.console.GeneralComponents._



  class AwsInstanceInfo(instanceInfo: com.amazonaws.services.autoscaling.model.Instance) extends AnyInstanceInfo {

    override def printState: NodeSeq = {
      <p>{instanceInfo.getLifecycleState}</p>
    }

    override def namespace: Namespace = Namespace.root

    override def instanceId: InstanceId = {
      InstanceId(instanceInfo.getInstanceId)
    }
  }


  override type InstanceInfo = AwsInstanceInfo

  override def printLog(instanceId: String, namespaceRaw: Seq[String]): NodeSeq = {
    val namespace = Namespace(namespaceRaw)
    val logContent = awsCompota.configuration.loggingDestination(InstanceId(instanceId), namespace) match {
      case Some(s3Object) => env.awsClients.s3.getObjectString(s3Object).recoverWith { case t =>
        Failure(new Error("couldn't retrieve log for instance: " + instanceId + " namespace: " + namespace.getPath, t))
      }
      case None => {
        Failure(new Error("couldn't retrieve log for instance: " + instanceId + " namespace: " + namespace.getPath))
      }
    }
    preResult(logContent)
  }

  override def shutdown(): Unit = {}

  override def getLogRaw(instanceId: String, namespaceRaw: Seq[String]): Try[Either[URL, String]] = {
    val namespace = Namespace(namespaceRaw)
    awsCompota.configuration.loggingDestination(InstanceId(instanceId), namespace) match {
      case Some(s3object) => {
        env.awsClients.s3.generateTemporaryLink(s3object, Duration(10, MINUTES)).map { url => Left(url) }.recoverWith { case t =>
          Failure(new Error("couldn't retrieve log for instance: " + instanceId + " namespace: " + namespace.getPath, t))
        }
      }
      case None => {
        Failure(new Error("couldn't retrieve log for instance: " + instanceId + " namespace: " + namespace.getPath))
      }
    }
  }

  override def sidebar: NodeSeq = {
    <ul class="nav nav-sidebar">
      <li><a href="/"><strong>home</strong></a></li>
    </ul>
      <ul class="nav nav-sidebar">
        {nisperosLinks}
      </ul>
      <ul class="nav nav-sidebar">
        <li><a href="/errorsPage">errors</a></li>
        <li><a href="#" class="undeploy">undeploy</a></li>
      </ul>
  }

  override def stackTraceInstance(instanceId: String, namespace: Seq[String]): NodeSeq = {
    preResult(Failure(new Error("stack traces are not supported in AWS compota console")))
  }


  override def listWorkers(nispero: N, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[AwsInstanceInfo])] = {
    
    AutoScalingUtils.describeInstances(env.awsClients.as.as, nispero.configuration.workerAutoScalingGroup.name, lastToken, limit).map { case (lToken, instances) =>
      (lToken, instances.map { i => new AwsInstanceInfo(i) })
    }
  }

  override def listManagers(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[AwsInstanceInfo])] = {
    AutoScalingUtils.describeInstances(env.awsClients.as.as, awsCompota.configuration.managerAutoScalingGroup.name, lastToken, limit).map { case (lToken, instances) =>
      (lToken, instances.map { i => new AwsInstanceInfo(i) })
    }
  }



  override def nisperoProperties(nispero: N): Map[String, NodeSeq] = {
    autoScalingGroupProperties(nispero.configuration.workerAutoScalingGroup)
  }

  override def metamanagerProperties(): Map[String, NodeSeq] = {
    autoScalingGroupProperties(awsCompota.configuration.managerAutoScalingGroup)
  }

  override def compotaProperties(): Map[String, NodeSeq] = {
    Map(
      "notifications e-mail" -> <p>{awsCompota.configuration.notificationEmail}</p>,
      "timeout" -> <p>{awsCompota.configuration.timeout.toMinutes + " mins"}</p>,
      "local error threshold" -> <p>{awsCompota.configuration.localErrorThreshold}</p>,
      "global error threshold" -> <p>{awsCompota.configuration.globalErrorThreshold}</p>
    )
  }

  def autoScalingGroupProperties(group: AutoScalingGroup): Map[String, NodeSeq] = {
    Map(
      "auto scaling group name" -> <p>{group.name}</p>,
      "desired capacity" -> <p>{group.desiredCapacity}</p>,
      "instance type" -> <p>{group.launchingConfiguration.instanceSpecs.instanceType}</p>,
      "ssh key pair" -> <p>{group.launchingConfiguration.instanceSpecs.keyName}</p>
    )
  }

  override def namespacePage: NodeSeq = errorDiv(logger, "namespaces are not supported by AWS compota console")

  override def printNamespaces(lastToken: Option[String]): NodeSeq = errorDiv(logger, "namespaces are not supported by AWS compota console")

  override def terminateInstance(instanceId: String, namespace: Seq[String]): NodeSeq = {
    preResult(Try {
      env.awsClients.ec2.terminateInstance(instanceId)
      "instance " + instanceId + " terminated"
    })
  }

  override def sshInstance(instanceId: String, namespace: Seq[String]): NodeSeq = {
    val tryS: Try[String] = Success(()).flatMap { u =>
      env.awsClients.ec2.getInstanceById(instanceId).flatMap { instance =>
        instance.getSSHCommand()
      } match {
        case Some(s) => Success(s)
        case None => Failure(new Error("couldn't retrive ssh command for instance: " + instanceId))
      }
    }
    preResult(tryS)
  }
}
