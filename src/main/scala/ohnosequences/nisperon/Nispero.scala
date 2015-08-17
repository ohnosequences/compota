package ohnosequences.nisperon

import ohnosequences.nisperon.queues.{MonoidQueueAux, MonoidQueue}
import ohnosequences.nisperon.bundles._
import ohnosequences.nisperon.logging.FailTable

import ohnosequences.statika.aws.amazonLinuxAMIs.AmazonLinuxAMIOps
import ohnosequences.statika.instructions._

import org.clapper.avsl.Logger


trait NisperoAux {

  type IQ <: MonoidQueueAux

  type OQ <: MonoidQueueAux

  val inputQueue: IQ

  val outputQueue: OQ

  type I <: InstructionsAux

  val instructions: I

  val nisperoConfiguration: NisperoConfiguration

  type W <: WorkerAux

  val worker: W

  type M <: ManagerAux

  val manager: M

  val aws: AWS

  def installManager()

  def installWorker()

  def deploy()
}

class Nispero[Input, Output, InputQueue <: MonoidQueue[Input], OutputQueue <: MonoidQueue[Output]](
                                                                                                    val aws: AWS,
                                                                                                    val inputQueue: InputQueue,
                                                                                                    val outputQueue: OutputQueue,
                                                                                                    val instructions: Instructions[Input, Output],
                                                                                                    val nisperoConfiguration: NisperoConfiguration
                                                                                                    ) extends NisperoAux {

  type IQ = InputQueue

  type OQ = OutputQueue

  type I = Instructions[Input, Output]

  type W = Worker[Input, Output, InputQueue, OutputQueue]

  type M = Manager

  val worker = new Worker(aws, inputQueue, outputQueue, instructions, nisperoConfiguration)

  val manager = new Manager(aws, nisperoConfiguration)

  val logger = Logger(this.getClass)

  object instructionsBundle extends InstructionsBundle(instructions)

  object workerBundle extends WorkerBundle(instructionsBundle) {

    override def install: Results = {
      worker.runInstructions()
      success(s"Module ${bundleFullName} is installed")
    }

  }

  object managerBundle extends ManagerBundle {

    val logger = Logger(this.getClass)

    override def install: Results = {
      val failTable = new FailTable(aws, nisperoConfiguration.nisperonConfiguration.errorTable)
      try {
        val workersGroup = nisperoConfiguration.workerGroup

        logger.info("nispero " + nisperoConfiguration.name + ": generating user script")
        val env = workerCompatible.environment
        val envOps = AmazonLinuxAMIOps(workerCompatible)
        val script = envOps.userScript

        logger.info("nispero " + nisperoConfiguration.name + ": launching workers group")
        val workers = workersGroup.autoScalingGroup(
          name = nisperoConfiguration.workersGroupName,
          defaultInstanceSpecs = nisperoConfiguration.nisperonConfiguration.defaultInstanceSpecs,
          amiId = workerCompatible.environment.amiVersion,
          userData = script
        )

        aws.as.createAutoScalingGroup(workers)

        logger.info("starting control queue handler")

        manager.runControlQueueHandler()

      } catch {
        case t: Throwable =>
          Nisperon.reportFailure(aws, nisperoConfiguration.nisperonConfiguration, "manager", t, true, failTable)
      }

      success(s"Module ${bundleFullName} is installed")
    }

  }

  object managerCompatible extends ManagerCompatible(
    managerBundle,
    nisperoConfiguration.nisperonConfiguration.metadataBuilder.build("manager", nisperoConfiguration.name)
  )

  object workerCompatible extends WorkerCompatible(
    workerBundle,
    nisperoConfiguration.nisperonConfiguration.metadataBuilder.build("worker", nisperoConfiguration.name, nisperoConfiguration.nisperonConfiguration.workingDir)
  )


  def installManager() {

    managerBundle.install
  }

  def installWorker() {
    workerBundle.install
  }

  def deploy() {

    val managerGroup = nisperoConfiguration.nisperonConfiguration.managerGroupConfiguration

    logger.info("nispero " + nisperoConfiguration.name + ": generating user script")
    val env = managerCompatible.environment
    val envOps = AmazonLinuxAMIOps(managerCompatible)
    val script = envOps.userScript

    logger.info("nispero " + nisperoConfiguration.name + ": launching manager group")
    val managerASGroup = managerGroup.autoScalingGroup(
      name = nisperoConfiguration.managerGroupName,
      defaultInstanceSpecs = nisperoConfiguration.nisperonConfiguration.defaultInstanceSpecs,
      amiId = managerCompatible.environment.amiVersion,
      userData = script
    )

    aws.as.createAutoScalingGroup(managerASGroup)
  }


}
