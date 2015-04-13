package ohnosequences.compota.local.metamanager

import java.util.concurrent.ConcurrentHashMap

import ohnosequences.compota.metamanager.{BaseMetaManager, AnyMetaManager}
import ohnosequences.compota.queues.{AnyQueueOp, AnyQueueReducer}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import ohnosequences.compota.local.{LocalCompota, AnyLocalNispero, ThreadEnvironment}

import scala.util.{Failure, Success, Try}


class LocalMetaManager[U](
                        val compota: LocalCompota[U],
                        nisperoEnvironments: ConcurrentHashMap[String, ConcurrentHashMap[String, ThreadEnvironment]]
                        ) extends BaseMetaManager {

  override type MetaManagerEnvironment = ThreadEnvironment
  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerCompota = LocalCompota[U]





}
