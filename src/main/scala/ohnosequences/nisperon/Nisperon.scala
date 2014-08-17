package ohnosequences.nisperon


import ohnosequences.nisperon.queues._
import scala.collection.mutable
import ohnosequences.awstools.s3.ObjectAddress
import java.io.{PrintWriter, File}
import ohnosequences.nisperon.bundles.{WhateverBundle, NisperonMetadataBuilder}
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.sqs.model.DeleteQueueRequest
import ohnosequences.awstools.ec2.InstanceType
import ohnosequences.awstools.ddb.Utils
import com.typesafe.scalalogging.{Logger, LazyLogging}
import ohnosequences.nisperon.logging.FailTable
import ohnosequences.nisperon.logging.InstanceLogging


abstract class Nisperon extends LazyLogging {

  val nisperos = mutable.HashMap[String, NisperoAux]()

  val nisperonConfiguration: NisperonConfiguration

  val mergingQueues: List[MonoidQueueAux] = List[MonoidQueueAux]()

  val aws: AWS = new AWS()


  def checks()

 // val addressCreator: AddressCreator = DefaultAddressCreator

  class S3QueueLocal[T](name: String, monoid: Monoid[T], serializer: Serializer[T]) extends
     S3Queue(aws, Naming.s3name(nisperonConfiguration,  name), monoid, serializer,
       deadLetterQueueName = nisperonConfiguration.deadLettersQueue)

  def s3queue[T](name: String, monoid: Monoid[T], serializer: Serializer[T]) = {
    new S3QueueLocal(name, monoid, serializer)
  }

  class DynamoDBQueueLocal[T](name: String, monoid: Monoid[T], serializer: Serializer[T], writeBodyToTable: Boolean, throughputs: (Int, Int)) extends
    DynamoDBQueue(aws, Naming.name(nisperonConfiguration,  name), monoid, serializer, throughputs, deadLetterQueueName = nisperonConfiguration.deadLettersQueue)

  def queue[T](name: String, monoid: Monoid[T], serializer: Serializer[T], writeBodyToTable: Boolean = true, throughputs: (Int, Int) = (100, 100)) = {
    new DynamoDBQueueLocal(name, monoid, serializer, writeBodyToTable, throughputs)
  }

  //in secs
  def launchTime: Long = {
    if (nisperos.values.isEmpty) {
      0
    } else {
      val groupName = nisperos.values.head.nisperoConfiguration.managerGroupName
      aws.as.getCreatedTime(groupName).map(_.getTime) match {
        case Some(timestamp) => (System.currentTimeMillis() - timestamp) / 1000
        case None => 0
      }
    }
  }


  class NisperoWithDefaults[I, O, IQ <: MonoidQueue[I], OQ <: MonoidQueue[O]] (
    inputQueue: IQ, outputQueue: OQ, instructions: Instructions[I, O], nisperoConfiguration: NisperoConfiguration
  ) extends Nispero[I, O, IQ, OQ](aws, inputQueue, outputQueue, instructions, nisperoConfiguration)


  def nispero[I, O, IQ <: MonoidQueue[I], OQ <: MonoidQueue[O]] (
    inputQueue: IQ, outputQueue: OQ, instructions: Instructions[I, O], nisperoConfiguration: NisperoConfiguration
  ): Nispero[I, O, IQ, OQ] = {

    val r = new NisperoWithDefaults(inputQueue, outputQueue, instructions, nisperoConfiguration)
    nisperos.put(nisperoConfiguration.name, r)
    r
  }

  def undeployActions(force: Boolean): Option[String]

  def sendUndeployCommandToManagers(reason: String) {
    logger.info("sending undeploy messages to managers")
    val undeployMessage = JSON.toJSON(ManagerCommand("undeploy", reason))
    val wrap = JSON.toJSON(ValueWrap("1", undeployMessage))
    aws.sns.createTopic(nisperonConfiguration.controlTopic).publish(wrap)
  }

  //todo fix this ugly wrapping
  def sendUndeployCommand(reason: String, force: Boolean, notifyManagers: Boolean = false) {
    //aws.sns.sns.
    logger.info("sending undeploy message to metemanager")

    val command = JSON.toJSON(List(Undeploy(reason, force).marshall()))
    val wrap2 = JSON.toJSON(ValueWrap("undeploy", command))
    //send commad to metamanager
    aws.sqs.createQueue(nisperonConfiguration.metamanagerQueue).sendMessage(wrap2)
  }

  def checkQueues(): Either[MonoidQueueAux, List[MonoidQueueAux]] = {
    val graph = new NisperoGraph(nisperos)
    graph.checkQueues()
  }

  def notification(subject: String, message: String) {
    val topic = aws.sns.createTopic(nisperonConfiguration.notificationTopic)
    topic.publish(message, subject)
  }

  def addTasks(): Unit

  def main(args: Array[String]) {

    args.toList match {
      case "meta" :: "meta" :: Nil => new MetaManager(Nisperon.this).run()

      case "manager" :: nisperoId :: Nil => nisperos(nisperoId).installManager()
      case "worker" :: nisperoId :: Nil => nisperos(nisperoId).installWorker()

      case "run" :: Nil => {
        //check jar

          logger.info("creating notification topic: " + nisperonConfiguration.notificationTopic)
          val topic = aws.sns.createTopic(nisperonConfiguration.notificationTopic)

          logger.info("creating dead letter queue: " + nisperonConfiguration.deadLettersQueue)
        //  val deadLettersQueue = new SQSQueue[Unit](aws.sqs.sqs, nisperonConfiguration.deadLettersQueue, unitSerializer).createQueue()

          if (!topic.isEmailSubscribed(nisperonConfiguration.email)) {
            logger.info("subscribing " + nisperonConfiguration.email + " to notification topic")
            topic.subscribeEmail(nisperonConfiguration.email)
            logger.info("please confirm subscription")
          }

          logger.info("creating failures table")
          val failTable =  new FailTable(aws, nisperonConfiguration.errorTable)
          failTable.create()

          if(!aws.s3.objectExists(nisperonConfiguration.artifactAddress)) {
            throw new Error("jar isn't published: " + nisperonConfiguration.artifactAddress)
          }

          logger.info("creating bucket " + nisperonConfiguration.bucket)
          aws.s3.createBucket(nisperonConfiguration.bucket)

          nisperos.foreach {
            case (id, nispero) =>
              nispero.runManager()
          }

          val bundle = new WhateverBundle(Nisperon.this, "meta", "meta")
          val userdata = bundle.userScript(bundle)



          val metagroup = nisperonConfiguration.metamanagerGroupConfiguration.autoScalingGroup(
            name = nisperonConfiguration.metamanagerGroup,
            amiId = bundle.ami.id,
            defaultInstanceSpecs = nisperonConfiguration.defaultInstanceSpecs,
            userData = userdata
          )

          addTasks()

          logger.info("launching metamanager")
          //println(metagroup)

          aws.as.createAutoScalingGroup(metagroup)


         
         // notification(nisperonConfiguration.id + " started", "started")

          //todo fix closing
          System.exit(0)

      }

      case "check" :: "queues" :: Nil => {
        logger.info(checkQueues().toString)
      }

      case "graph" :: Nil => {
        logger.info(new NisperoGraph(nisperos).graph.toString)
      }

      case "add" :: "tasks" :: Nil => {
        addTasks()
      }

      case "undeploy" :: Nil => {
        sendUndeployCommand("adhoc", force = true)
      }

      case "undeploy" :: "force" :: Nil => {

        aws.as.deleteAutoScalingGroup(nisperonConfiguration.metamanagerGroup)
        aws.sns.createTopic(nisperonConfiguration.controlTopic).delete()
        nisperos.foreach {
          case (id, nispero) =>

            aws.as.deleteAutoScalingGroup(nispero.nisperoConfiguration.managerGroupName)
            aws.as.deleteAutoScalingGroup(nispero.nisperoConfiguration.workersGroupName)
            aws.sqs.createQueue(nispero.nisperoConfiguration.controlQueueName).delete()

        }
        logger.info("undeploy actions results: " + undeployActions(true))
      }

      case "list" :: Nil => {
        nisperos.foreach {
          case (id, nispero) => println( id + " -> " + nispero.nisperoConfiguration.workersGroupName)
        }
      }

      case "undeploy" :: "actions" :: Nil => undeployActions(false)

      case "checks":: Nil => checks()

      case "dot" :: "dot" :: Nil => {
        val dotFile = new StringBuilder()
        dotFile.append("digraph nisperon {\n")
        nisperos.foreach {
          case (id: String, nispero: NisperoAux) =>
            val i = nispero.inputQueue.name
            val o = nispero.outputQueue.name
            dotFile.append(i + " -> " + o + "[label=\"" + id + "\"]" + "\n")

        }
        dotFile.append("}\n")

        val printWriter = new PrintWriter("nisperon.dot")
        printWriter.print(dotFile.toString())
        printWriter.close()

        import sys.process._
        "dot -Tcmapx -onisperon.map -Tpng -onisperon.png nisperon.dot".!
      }

      case nispero :: "size" :: cons if nisperos.contains(nispero) => {
        val n = nisperos(nispero)
        aws.as.as.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest()
          .withAutoScalingGroupName(n.nisperoConfiguration.workersGroupName)
          .withDesiredCapacity(args(2).toInt)
        )
        nisperos(nispero)
      }

      case args => additionalHandler(args)

    }
  }



  def additionalHandler(args: List[String])


}

object Nisperon extends LazyLogging {

  def unsafeAction(name: String, action: => Unit, logger: Logger, limit: Int = 10) {

    var done = false
    var c =1
    while(!done && c < limit) {
      c += 1
      try {
        logger.info(name)
        action
        done = true
      } catch {
        case t: Throwable => {
          t.printStackTrace()
          logger.error(t.toString)
          logger.error("repeating")
        }
      }
    }

  }


  def reportFailure(aws: AWS, nisperonConfiguration: NisperonConfiguration, taskId: String, t: Throwable,  terminateInstance: Boolean, failTable: FailTable, messagePrefix: String = "", maxAttempts: Int = 10) {

    logger.error(messagePrefix + " " + t.toString + " " + t.getLocalizedMessage)
    t.printStackTrace()

    logger.error("reporting failure to failure table")
    var attempt = maxAttempts
    val instanceId = aws.ec2.getCurrentInstanceId.getOrElse("unknown" + System.currentTimeMillis())

    while(attempt > 0) {
      attempt -= 1
      try {
        failTable.fail(taskId, instanceId, messagePrefix + " " + t.toString)
        attempt = 0
      } catch {
        case t: Throwable => logger.error("can't write to fail table: " + t.toString)
      }
    }

    attempt = maxAttempts
    while(attempt > 0) {
      attempt -= 1
      try {
        InstanceLogging.putLog(aws, nisperonConfiguration, instanceId)
        attempt = 0
      } catch {
        case t: Throwable => logger.error("can't upload log")
      }
    }

    if(terminateInstance) {
      logger.error("terminating instance")
      aws.ec2.getCurrentInstance.foreach(_.terminate())
    }
  }

}
