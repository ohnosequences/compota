package ohnosequences.compota

import java.io.File

import ohnosequences.compota.queues._
import ohnosequences.compota.logging.{FailTable}
import ohnosequences.logging.ConsoleLogger

//todo use failed table failed if failed from several machines

abstract class WorkerAux {

  type IQ <: MonoidQueueAux

  type OQ <: MonoidQueueAux

  val inputQueue: IQ

  val outputQueue: OQ

  val instructions: Instructions[inputQueue.MA, outputQueue.MA]

  val nisperoConfiguration: NisperoConfiguration

  val aws: AWS

  val logger = new ConsoleLogger("worker")


  //todo add links to instance logs
  def runInstructions() {

    var start = true

    val failTable = new FailTable(aws, nisperoConfiguration.nisperonConfiguration.errorTable)

    try {
      logger.info("preparing instructions")
      val instructionsWorkingDir = new File(nisperoConfiguration.nisperonConfiguration.workingDir, "instructions")
      instructionsWorkingDir.mkdir()

      val context: instructions.Context = instructions.prepare(logger, instructionsWorkingDir, aws).get

      try {
        logger.info("initializing queues")
        inputQueue.initRead()
        outputQueue.initWrite()
      } catch {
        case t: Throwable =>
          logger.error("error during initializing queues")
          start = false
          Compota.reportFailure(aws, nisperoConfiguration.nisperonConfiguration, "worker", t, true, failTable, "error during initializing queues")
      }

      var startTime = 0L
      var endTime = 0L


      while (start) {

        var message: Message[inputQueue.MA] = null

        try {

          startTime = System.currentTimeMillis()
          message = inputQueue.read()
          endTime = System.currentTimeMillis()
          logger.info("message read in " + (endTime - startTime))


          logger.info("executing " + instructions + " instructions on " + message.id)
          //todo add check for empty messages
          //todo fix this check for a productqueue
          //val prefix = S3Logger.prefix(nisperoConfiguration.nisperonConfiguration, message.id)
          //val s3logger = new S3Logger(nisperoConfiguration.name, aws, prefix, nisperoConfiguration.nisperonConfiguration.workingDir)

          val s3logger = new ConsoleLogger(message.id)
          startTime = System.currentTimeMillis()
          val output = instructions.solve(s3logger, instructionsWorkingDir, context, message.value()).get
          endTime = System.currentTimeMillis()
          logger.info("executed in " + (endTime - startTime))

         // s3logger.close()


          startTime = System.currentTimeMillis()
          outputQueue.put(message.id, nisperoConfiguration.name, output)
          endTime = System.currentTimeMillis()
          logger.info("message written in " + (endTime - startTime))
          deleteMessage(message)


          //todo fix reset
        } catch {
          case t: Throwable => {
            if (message == null) {
              Compota.reportFailure(aws, nisperoConfiguration.nisperonConfiguration, "worker", t, true, failTable, "error during reading from queue")
            } else {
              if (failTable.fails(message.id) > nisperoConfiguration.nisperonConfiguration.errorThreshold) {
                logger.error("message " + message.id + " failed more than " + nisperoConfiguration.nisperonConfiguration.errorThreshold)
                deleteMessage(message)
              }
              Compota.reportFailure(aws, nisperoConfiguration.nisperonConfiguration, message.id, t, false, failTable)
            }
            inputQueue.reset()
          }
        }
      }

    } catch {
      case t: Throwable =>
        logger.error("error during preparing instructions")
        start = false
        Compota.reportFailure(aws, nisperoConfiguration.nisperonConfiguration, "worker", t, true, failTable, "error during preparing instructions")

    }
  }


  def deleteMessage(message: Message[inputQueue.MA]) {
    var deleted = false
    var attempt = 0
    while (!deleted) {
      try {
        attempt += 1
        message.delete()
        deleted = true
      } catch {
        case t: Throwable => {
          if (attempt < 10) {
            logger.warn("couldn't delete massage " + message.id)
            Thread.sleep(attempt * 100)
          } else {
            throw t
          }
        }
      }
    }
  }
}


class Worker[Input, Output, InputQueue <: MonoidQueue[Input], OutputQueue <: MonoidQueue[Output]]
(val aws: AWS, val inputQueue: InputQueue, val outputQueue: OutputQueue, val instructions: Instructions[Input, Output], val nisperoConfiguration: NisperoConfiguration) extends WorkerAux {
  type IQ = InputQueue

  type OQ = OutputQueue

}
