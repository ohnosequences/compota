package ohnosequences.compota.local.metamanager

import java.util.concurrent.ConcurrentHashMap

import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.metamanager.{BaseMetaManager, AnyMetaManager}
import ohnosequences.compota.queues.{AnyQueueOp, AnyQueueReducer}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import ohnosequences.compota.local.{AnyLocalCompota, LocalEnvironment, LocalCompota, AnyLocalNispero}

import scala.util.{Failure, Success, Try}


class LocalMetaManager[U](
                        val compota: AnyLocalCompota.of[U],
                        nisperoEnvironments: ConcurrentHashMap[InstanceId, (AnyLocalNispero, LocalEnvironment)]
                        ) extends BaseMetaManager {

  override type MetaManagerEnvironment = LocalEnvironment
  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerNispero = AnyLocalNispero
  override type MetaManagerCompota = AnyLocalCompota.of[U]





}
