package ohnosequences.compota.aws.metamanager

import ohnosequences.compota.Compota
import ohnosequences.compota.aws.AwsCompota
import ohnosequences.compota.serialization.{JSON, Serializer}

import scala.util.Try


//todo find some ADT to Json library
class CommandSerializer(compota: AwsCompota) extends Serializer[Command] {
  override def fromString(s: String): Try[Command] = Try{
    val commnad0: Command0 = JSON.extract[Command0](s).get
    commnad0 match {
      case Command0("worker", "group", "create", name) => CreateWorkerGroup(compota.nisperosNames(name))
    }
  }

  override def toString(t: Command): Try[String] = {
    val command0 = t match {
      case CreateWorkerGroup(nispero) =>  Command0("worker", "group", "create", nispero.name)
    }
    JSON.toJSON(command0)
  }
}

case class Command0(component: String, resource: String, action: String, arg: String)



