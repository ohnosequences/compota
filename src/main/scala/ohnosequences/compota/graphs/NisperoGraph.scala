package ohnosequences.compota.graphs


import ohnosequences.compota._
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues._

import scala.collection.mutable
import scala.util.Try

object NisperoGraph {
  def apply[E <: AnyEnvironment[E]](nisperos: Map[String, AnyNispero.of[E]]): NisperoGraph[E] = {
    val edges = mutable.ListBuffer[Edge[String, String]]()
    nisperos.foreach { case (nisperoName, nispero) =>

      nispero.inputQueue.subQueues.zipWithIndex.foreach { case (inputSubQueue, i) =>
        nispero.outputQueue.subQueues.zipWithIndex.foreach { case (outputSubQueue, j) =>
          edges += Edge(nispero.configuration.name + "_" + i + "_" + j, Node(inputSubQueue.name), Node(outputSubQueue.name))
        }
      }
    }
    new NisperoGraph[E](new Graph(edges.toList), nisperos)
  }
}


class NisperoGraph[E <: AnyEnvironment[E]](
                                            val graph: Graph[String, String],
                                            val nisperos: Map[String, AnyNispero.of[E]]
                                            ) {

  val sortedQueues: List[Node[String]] = graph.sort

  val notLeafsQueues: List[String] = sortedQueues.filterNot(graph.out(_).isEmpty).map(_.label)

}

object QueueChecker {
  def apply[E <: AnyEnvironment[E]](env: E, nisperoGraph: NisperoGraph[E]): Try[QueueChecker[E]] = {
    Try {
      val queueOps = new mutable.HashMap[String, AnyQueueOp]()
      nisperoGraph.nisperos.foreach { case (name, nispero) =>
        val inputQueueOp = nispero.inputQueue.create(nispero.inputContext(env)).get
        val outputQueueOp = nispero.outputQueue.create(nispero.outputContext(env)).get

        inputQueueOp.subOps().foreach { subOp =>
          queueOps.put(subOp.queue.name, subOp)
        }

        outputQueueOp.subOps().foreach { subOp =>
          queueOps.put(subOp.queue.name, subOp)
        }
      }

      new QueueChecker(nisperoGraph, queueOps.toMap, nisperoGraph.notLeafsQueues.map { queueName =>
        queueOps.get(queueName).get
      })
    }
  }
}

class QueueChecker[E <: AnyEnvironment[E]](val nisperoGraph: NisperoGraph[E],
                                           val queueOps: Map[String, AnyQueueOp],
                                           val notLeafsQueueOps: List[AnyQueueOp]) {



  //return either first not empty queue, either all not-leafs (to delete them)
  def checkQueues(): Try[Either[AnyQueueOp, List[AnyQueueOp]]] = Try {

    notLeafsQueueOps.find { queueOp =>
      !queueOp.isEmpty.get
    } match {
      case None => Right(notLeafsQueueOps)
      case Some(queueOp) => Left(queueOp)
    }
  }

}

