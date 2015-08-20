package ohnosequences.compota

import ohnosequences.compota.queues._
import ohnosequences.compota.logging.FailTable
import ohnosequences.compota.logging.InstanceLogging
import ohnosequences.compota.bundles.{MetaManagerCompatible, MetaManagerBundle}

import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.logging.{Logger, ConsoleLogger}

import ohnosequences.statika.aws.amazonLinuxAMIs.AmazonLinuxAMIOps
import ohnosequences.statika.instructions
import ohnosequences.statika.instructions.Results

import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest

import scala.collection.mutable
import scala.util.Success

import java.io.{PrintWriter, File}


abstract class Compota {

  val nisperos = mutable.HashMap[String, NisperoAux]()

  val compotaConfiguration: CompotaConfiguration

  val mergingQueues: List[MonoidQueueAux] = List[MonoidQueueAux]()

  val credentialsFile = new File(System.getProperty("user.home"), "nispero.credentials")

  val aws: AWS = new AWS(credentialsFile)

  val logger = new ConsoleLogger("compota")

  def checkConfiguration(verbose: Boolean): Boolean = {
    aws.s3.objectExists(compotaConfiguration.artifactAddress) match {
      case Success(true) => true
      case _ => throw new Error("jar isn't published: " + compotaConfiguration.artifactAddress)
    }
  }

  class S3Queue[T](name: String, monoid: Monoid[T], serializer: Serializer[T]) extends
  S3QueueAbstract(aws, Naming.s3name(compotaConfiguration, name), monoid, serializer,
    deadLetterQueueName = compotaConfiguration.deadLettersQueue) {

  }

  class DynamoDBQueue[T](name: String, monoid: Monoid[T], serializer: Serializer[T], writeBodyToTable: Boolean = true, throughputs: (Int, Int)) extends
  DynamoDBQueueAbstract(aws, Naming.name(compotaConfiguration, name), monoid, serializer, throughputs, deadLetterQueueName = compotaConfiguration.deadLettersQueue)


  class S3MapQueue[K, V](name: String, monoid: Monoid[V], kSerializer: Serializer[K],
                         vSerializer: Serializer[V], incrementSerializer: IncrementalSerializer[V])
    extends S3MapQueueAbstract[K, V](aws, Naming.name(compotaConfiguration, name), monoid, kSerializer,
      vSerializer, incrementSerializer, ObjectAddress(compotaConfiguration.bucket, name))


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


  class NisperoWithDefaults[I, O, IQ <: MonoidQueue[I], OQ <: MonoidQueue[O]](
                                                                               inputQueue: IQ, outputQueue: OQ, instructions: Instructions[I, O], nisperoConfiguration: NisperoConfiguration
                                                                               ) extends Nispero[I, O, IQ, OQ](aws, inputQueue, outputQueue, instructions, nisperoConfiguration)


  def nispero[I, O, IQ <: MonoidQueue[I], OQ <: MonoidQueue[O]](
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
    aws.sns.createTopic(compotaConfiguration.controlTopic).publish(wrap)
  }

  //todo fix this ugly wrapping
  def sendUndeployCommand(reason: String, force: Boolean, notifyManagers: Boolean = false) {
    //aws.sns.sns.
    logger.info("sending undeploy message to metemanager")

    val command = JSON.toJSON(List(Undeploy(reason, force).marshall()))
    val wrap2 = JSON.toJSON(ValueWrap("undeploy", command))
    //send command to metamanager
    aws.sqs.createQueue(compotaConfiguration.metamanagerQueue).sendMessage(wrap2)
  }

  def checkQueues(): Either[MonoidQueueAux, List[MonoidQueueAux]] = {
    val graph = new NisperoGraph(nisperos)
    graph.checkQueues()
  }

  def notification(subject: String, message: String) {
    val topic = aws.sns.createTopic(compotaConfiguration.notificationTopic)
    topic.publish(message, subject)
  }

  def addTasks(): Unit

  def checkTasks(verbose: Boolean): Boolean = true

  object metaManagerBundle extends MetaManagerBundle {

    override def install: Results = {

      instructions.success(s"Module ${bundleFullName} is installed")
    }

  }

  object metaManagerCompatible extends MetaManagerCompatible(
    metaManagerBundle,
    compotaConfiguration.metadataBuilder.build("meta", "meta")
  )

  def main(args: Array[String]) {

    args.toList match {
      case "meta" :: "meta" :: Nil => new MetaManager(Compota.this).run()

      case "manager" :: nisperoId :: Nil => nisperos(nisperoId).installManager()

      case "worker" :: nisperoId :: Nil => nisperos(nisperoId).installWorker()

      case "run" :: Nil => {

        logger.info("creating notification topic: " + compotaConfiguration.notificationTopic)
        val topic = aws.sns.createTopic(compotaConfiguration.notificationTopic)

        logger.info("creating dead letter queue: " + compotaConfiguration.deadLettersQueue)
        //  val deadLettersQueue = new SQSQueue[Unit](aws.sqs.sqs, compotaConfiguration.deadLettersQueue, unitSerializer).createQueue()

        if (!topic.isEmailSubscribed(compotaConfiguration.email)) {
          logger.info("subscribing " + compotaConfiguration.email + " to notification topic")
          topic.subscribeEmail(compotaConfiguration.email)
          logger.info("please confirm subscription")
        }

        logger.info("creating failures table")
        val failTable = new FailTable(aws, compotaConfiguration.errorTable)
        failTable.create()

        logger.info("creating bucket " + compotaConfiguration.bucket)
        aws.s3.createBucket(compotaConfiguration.bucket)

        nisperos.foreach {
          case (id, nispero) =>
            logger.info("deploying nispero " + id)
            nispero.deploy()
        }


        val env = metaManagerCompatible.environment
        val envOps = AmazonLinuxAMIOps(metaManagerCompatible)
        val userData = envOps.userScript


        val metagroup = compotaConfiguration.metamanagerGroupConfiguration.autoScalingGroup(
          name = compotaConfiguration.metamanagerGroup,
          amiId = metaManagerCompatible.environment.amiVersion,
          defaultInstanceSpecs = compotaConfiguration.defaultInstanceSpecs,
          userData = userData
        )

        addTasks()

        logger.info("launching metamanager")
        aws.as.createAutoScalingGroup(metagroup)

      }

      case "check" :: "queues" :: Nil => {
        logger.info(checkQueues().toString)
      }

      case "graph" :: Nil => {
        logger.info(new NisperoGraph(nisperos).graph.toString())
      }

      case "add" :: "tasks" :: Nil => {
        addTasks()
      }

      case "undeploy" :: Nil => {
        sendUndeployCommand("adhoc", force = true)
      }

      case "undeploy" :: "force" :: Nil => {

        aws.as.deleteAutoScalingGroup(compotaConfiguration.metamanagerGroup)
        aws.sns.createTopic(compotaConfiguration.controlTopic).delete()
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
          case (id, nispero) => println(id + " -> " + nispero.nisperoConfiguration.workersGroupName)
        }
      }

      case "undeploy" :: "actions" :: Nil => undeployActions(false)

      case "check" :: "tasks" :: Nil => {
        println("tasks are ok: " + checkTasks(verbose = true))
      }

      case "dot" :: "dot" :: Nil => {
        val dotFile = new StringBuilder()
        dotFile.append("digraph compota {\n")
        nisperos.foreach {
          case (id: String, nispero: NisperoAux) =>
            val i = nispero.inputQueue.name
            val o = nispero.outputQueue.name
            dotFile.append(i + " -> " + o + "[label=\"" + id + "\"]" + "\n")

        }
        dotFile.append("}\n")

        val printWriter = new PrintWriter("compota.dot")
        printWriter.print(dotFile.toString())
        printWriter.close()

        import sys.process._
        "dot -Tcmapx -onisperon.map -Tpng -onisperon.png compota.dot".!
      }

      case nispero :: "size" :: cons if nisperos.contains(nispero) => {
        val n = nisperos(nispero)
        aws.as.as.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest()
          .withAutoScalingGroupName(n.nisperoConfiguration.workersGroupName)
          .withDesiredCapacity(args(2).toInt)
        )
        nisperos(nispero)
      }

      case otherArgs => additionalHandler(otherArgs)

    }
  }


  def additionalHandler(args: List[String])


}

object Compota {

  def unsafeAction(name: String, action: => Unit, logger: Logger, limit: Int = 10) {

    var done = false
    var c = 1
    while (!done && c < limit) {
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


  def reportFailure(aws: AWS, nisperonConfiguration: CompotaConfiguration, taskId: String, t: Throwable, terminateInstance: Boolean, failTable: FailTable, messagePrefix: String = "", maxAttempts: Int = 10) {

    val logger = new ConsoleLogger("report failure")

    logger.error(messagePrefix + " " + t.toString + " " + t.getLocalizedMessage)
    t.printStackTrace()

    logger.error("reporting failure to failure table")
    var attempt = maxAttempts
    val instanceId = aws.ec2.getCurrentInstanceId.getOrElse("unknown" + System.currentTimeMillis())

    while (attempt > 0) {
      attempt -= 1
      try {
        failTable.fail(taskId, instanceId, messagePrefix + " " + t.toString)
        attempt = 0
      } catch {
        case t: Throwable => logger.error("can't write to fail table: " + t.toString)
      }
    }

    attempt = maxAttempts
    while (attempt > 0) {
      attempt -= 1
      try {
        InstanceLogging.putLog(aws, nisperonConfiguration, instanceId)
        attempt = 0
      } catch {
        case t: Throwable => logger.error("can't upload log")
      }
    }

    if (terminateInstance) {
      logger.error("terminating instance")
      aws.ec2.getCurrentInstance.foreach(_.terminate())
    }
  }

}
