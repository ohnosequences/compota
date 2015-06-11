package ohnosequences.compota.local.metamanager


import ohnosequences.compota.metamanager.{BaseMetaManagerCommand, BaseMetaManager}
import ohnosequences.compota.local._

class LocalMetaManager[U](val compota: AnyLocalCompota.of[U]) extends BaseMetaManager {

  override type MetaManagerEnvironment = LocalEnvironment
  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerNispero = AnyLocalNispero
  override type MetaManagerCompota = AnyLocalCompota.of[U]
  override type MetaManagerControlQueueContext = LocalContext

  override def controlQueueContext(env: MetaManagerEnvironment): MetaManagerControlQueueContext = env.localContext


  override val controlQueue
  =  new LocalQueue[BaseMetaManagerCommand]("control_queue", visibilityTimeout = compota.configuration.visibilityTimeout)

}
