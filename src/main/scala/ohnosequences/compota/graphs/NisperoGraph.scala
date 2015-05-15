package ohnosequences.compota.graphs


import ohnosequences.compota._
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues._

import scala.collection.mutable
import scala.util.Try

//todo add product queue
object NisperoGraph {
  def apply[E <: AnyEnvironment](env: E, nisperos: List[AnyNispero.of[E]]): Try[NisperoGraph] = {
    val nisperoNames = new mutable.HashMap[String, AnyNispero]()
    val queuesNames = new mutable.HashMap[String, AnyQueueOp]()
    val edges = mutable.ListBuffer[Edge[String, String]]()

    Try {
      nisperos.foreach { nispero =>
        nisperoNames.put(nispero.name, nispero)
        queuesNames.put(nispero.inputQueue.name, nispero.inputQueue.create(nispero.inContext(env)).get)
        queuesNames.put(nispero.outputQueue.name, nispero.outputQueue.create(nispero.outContext(env)).get)
        edges += Edge(nispero.name, Node(nispero.inputQueue.name), Node(nispero.outputQueue.name))
      }
      new NisperoGraph(new Graph(edges.toList), nisperoNames.toMap, queuesNames.toMap)
    }
  }
}


class NisperoGraph(graph: Graph[String, String], nisperos: Map[String, AnyNispero], val queues: Map[String, AnyQueueOp]) {

  val sortedQueueNames = graph.sort

  val queueOps: List[AnyQueueOp] = {
    sortedQueueNames.map { node =>
      queues(node.label)
    }
  }

  //return either first not empty queue, either all not-leafs (to delete them)
  def checkQueues(env: AnyEnvironment): Try[Either[AnyQueueOp, List[AnyQueueOp]]] = Try {
    val notLeafsQueues: List[AnyQueueOp] = sortedQueueNames.filterNot(graph.out(_).isEmpty).map { node =>
      queues(node.label)
    }
    notLeafsQueues.find { queue => !queue.isEmpty.get  } match {
      case None =>  Right(notLeafsQueues)
      case Some(queueOp) => Left(queueOp)
    }
  }
}

