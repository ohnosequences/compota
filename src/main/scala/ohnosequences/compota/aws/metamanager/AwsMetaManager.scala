package ohnosequences.compota.aws.metamanager

import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.AnyCompota
import ohnosequences.compota.aws.{AwsCompota, AnyAwsNispero, AwsEnvironment}
import ohnosequences.compota.local.LocalCompota
import ohnosequences.compota.metamanager.{BaseMetaManager, AnyMetaManager}
import ohnosequences.compota.queues.{AnyQueueOp, Queue}

import scala.util.{Success, Failure, Try}


class AwsMetaManager[U](val compota: AwsCompota[U], queues: List[AnyQueueOp]) extends BaseMetaManager {

  override type MetaManagerEnvironment = AwsEnvironment

  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerCompota = AwsCompota[U]
}
