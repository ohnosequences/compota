package ohnosequences.compota

object Namespace {
  val separator = "."

  val metaManager = new Namespace("metamanager")

  val controlQueue = new Namespace("control_queue")

  val terminationDaemon = new Namespace("termination_daemon")

  val unDeployActions = new Namespace("undeploy_actions")

  val worker = new Namespace("worker")
}

class Namespace(val stringRep: String) {  namespace =>
  def /(subSpace: Namespace): Namespace = {
    new Namespace(stringRep + Namespace.separator + subSpace.stringRep)
  }

  def /(subSpace: String): Namespace = {
    new Namespace(stringRep + Namespace.separator + subSpace)
  }

  override def toString: String = {
    stringRep
  }

  def toS3Key = stringRep.reverse
}


