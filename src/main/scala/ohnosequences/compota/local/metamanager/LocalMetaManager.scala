package ohnosequences.compota.local.metamanager

import java.util.concurrent.ConcurrentHashMap

import ohnosequences.compota.metamanager.{BaseMetaManager, AnyMetaManager}
import ohnosequences.compota.queues.{AnyQueueOp, AnyQueueReducer}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import ohnosequences.compota.local.{LocalEnvironment, LocalCompota, AnyLocalNispero}

import scala.util.{Failure, Success, Try}


class LocalMetaManager[U](
                        val compota: LocalCompota[U],
                        nisperoEnvironments: ConcurrentHashMap[String, ConcurrentHashMap[String, LocalEnvironment]]
                        ) extends BaseMetaManager {

  override type MetaManagerEnvironment = LocalEnvironment
  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerCompota = LocalCompota[U]





}
