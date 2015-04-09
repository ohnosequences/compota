package ohnosequences.compota.aws.metamanager

import ohnosequences.compota.Compota
import ohnosequences.compota.aws.AwsCompota
import ohnosequences.compota.serialization.{JSON, Serializer}

import scala.util.Try


//todo find some ADT to Json library
class CommandSerializer(compota: AwsCompota) extends Serializer[AwsCommand] {
  override def fromString(s: String): Try[AwsCommand] = Try{
    val commnad0: Command0 = JSON.extract[Command0](s).get
    commnad0 match {
      case Command0("worker", "create", "group" :: name :: Nil) => CreateWorkerGroup(compota.nisperosNames(name))
      case Command0("metamanager", "undeploy", reason :: force :: Nil) => UnDeploy(reason, force.toBoolean)
    }
  }

  override def toString(t: AwsCommand): Try[String] = {
    val command0 = t match {
      case CreateWorkerGroup(nispero) =>  Command0("worker", "create", List("group"))
      case UnDeploy(reason, force) =>  Command0("metamanager", "undeploy", List(reason, force.toString))
    }
    JSON.toJSON(command0)
  }
}

case class Command0(component: String, action: String, args: List[String])



