package ohnosequences.compota

object Namespace {

  val separator: Char = '|'

  val root = Namespace(List[String]())

  val metaManager = "metamanager"

  val controlQueue = "control_queue"

  val console = "console"

  val queueChecker = "queue_checker"

  val terminationDaemon = "termination_daemon"

  val unDeployActions = "undeploy_actions"

  val logUploader = "logUploader"

  val worker = "worker"

  def unapply(list: List[String]): Namespace = Namespace(list)

  def apply(s: String, separator: Char = Namespace.separator): Namespace = {
    if (s.isEmpty) {
      root
    } else {
      Namespace(s.split(separator).toList)
    }
  }

  def apply(seq: Seq[String]): Namespace = Namespace(seq.toList)
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
  
  def serialize(separator: Char = Namespace.separator): String = {
    parts match {
      case Nil => ""
      case part :: Nil => part
      case part :: tail => parts.reduce(_ + separator  + _)
    }
  }
  
  def getPath: String = {
    serialize('/')
  }

  override def toString: String = getPath

}


