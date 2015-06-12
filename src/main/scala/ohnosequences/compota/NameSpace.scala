package ohnosequences.compota

object Namespace {

  val separator = "."

  val root = Namespace(List[String]())

  val metaManager = "metamanager"

  val controlQueue = "control_queue"

  val terminationDaemon = "termination_daemon"

  val unDeployActions = "undeploy_actions"

  val logUploader = "logUploader"

  val worker = "worker"

  def unapply(list: List[String]): Namespace = Namespace(list)
}



case class Namespace(parts: List[String]) {  namespace =>
//  def /(subSpace: Namespace): Namespace = {
//    new Namespace(stringRep + Namespace.separator + subSpace.stringRep, parts ++ List(stringRep))
//  }



  def /(subSpace: String): Namespace = {
    Namespace(parts ++ List(subSpace))
  }

  def /(subSpace: Namespace): Namespace = {
    Namespace(parts ++ subSpace.parts)
  }

  override def toString: String = {
    parts match {
      case Nil => ""
      case part :: Nil => part
      case part :: tail => parts.reduce(_ + Namespace.separator  + _)
    }
  }

}


