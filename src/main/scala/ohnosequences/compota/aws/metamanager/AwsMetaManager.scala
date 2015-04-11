package ohnosequences.compota.aws.metamanager

import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.aws.AwsEnvironment
import ohnosequences.compota.local.ThreadEnvironment
import ohnosequences.compota.metamanager.AnyMetaManager
import ohnosequences.compota.queues.Queue

import scala.util.{Success, Failure, Try}


class AwsMetaManager(unDeployActions: (Boolean,AwsEnvironment) => Try[Unit]) extends AnyMetaManager {
  override type MetaManagerEnvironment = AwsEnvironment
  override type MetaManagerCommand = AwsCommand

  val unDeployActionStarted = new AtomicBoolean(false)
  val unDeployActionForce = new AtomicBoolean(false)



  override def process(command: AwsCommand, env: AwsEnvironment): Try[List[AwsCommand]] = {
    val logger = env.logger
    command match {
      case UnDeployActions(force) => {
        if(unDeployActionStarted.get()) {
          if(unDeployActionForce.get()) {
            logger.info("undeploy actions has been already started")
            Success(List[AwsCommand]())
          } else if (force) {
            logger.info("running undeploy actions")
            unDeployActionForce.set(true)
            unDeployActions(force, env).map { res =>
              List(FinishCompota)
            }
          } else {
            logger.info("undeploy actions has been already started")
            Success(List[AwsCommand]())
          }
        } else {
          logger.info("running undeploy actions")
          unDeployActionForce.set(force)
          unDeployActionStarted.set(true)
          unDeployActions(force, env).map { res =>

            List(FinishCompota)
          }
        }
      }
      case _ => {
        Failure(new Error("unknown command"))
      }

    }
  }


}
