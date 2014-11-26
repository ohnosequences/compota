package ohnosequences.compota.worker

import ohnosequences.compota.enviroment.Instance
import ohnosequences.compota.logging.{ConsoleLogger, Logger, S3Logger}
import ohnosequences.compota.{Nispero, NisperoAux}
import ohnosequences.compota.queues.Queue

import scala.util.{Success, Failure}

//instructions executor

trait WorkerAux {
  def start(instance: Instance)
}

class Worker[In, Out, InQueue <: Queue[In], OutQueue <: Queue[Out]](nispero: Nispero[In, Out, InQueue, OutQueue]) extends WorkerAux {


  def start(instance: Instance): Unit = {
    val logger = instance.getLogger
    logger.info("worker " + nispero.name + " started on instance " + instance.getId)
//    nispero.inputQueue.getReader match {
//      case Failure(t) => {
//        instance.fatalError(t)
//      }
//      case Success(queueReader) => {
//        nispero.outputQueue.getWriter match {
//          case Failure(t) => {
//            instance.fatalError(t)
//          }
//          case Success(queueWriter) => {

    nispero.inputQueue.getReader.foreach { queueReader =>
      nispero.outputQueue.getWriter.foreach { queueWriter =>
            val context  = nispero.instructions.prepare(logger)


            while(!instance.isTerminated) {
              queueReader.getMessage.foreach { message =>
                  message.getBody.foreach { input =>
                    logger.info("input: " + input)
                    nispero.instructions.solve(logger, context, input).foreach { output =>
                      logger.info("result: " + output)
                      output.foreach { e => queueWriter.write(List(e))}
                      nispero.inputQueue.deleteMessage(message)
                    }
                  }
              }
            }

          }
        }





  }

  //def prepared(queueReader: nispero.inputQueue.QR, queueWriter: )
}
