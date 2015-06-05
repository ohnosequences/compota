package ohnosequences.compota.graphs


import ohnosequences.compota._
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues._

import scala.collection.mutable
import scala.util.Try

//todo add product queue
object NisperoGraph {
  def apply[E <: AnyEnvironment[E]](env: E, nisperos: List[AnyNispero.of[E]]): Try[NisperoGraph] = {
    val nisperoNames = new mutable.HashMap[String, AnyNispero]()
    val queueOpNames = new mutable.HashMap[String, AnyQueueOp]()
    val queueOps = new mutable.HashMap[AnyQueue, List[AnyQueueOp]]()
    val edges = mutable.ListBuffer[Edge[String, String]]()

    Try {
      nisperos.foreach { nispero =>
        val nisperoConfiguration = nispero.configuration
        val nisperoName = nisperoConfiguration.name
        nisperoNames.put(nisperoName, nispero)
        val inputQueueOp = nispero.inputQueue.create(nispero.inputContext(env)).get
        val outputQueueOp = nispero.outputQueue.create(nispero.outputContext(env)).get

        inputQueueOp.subOps().foreach { subOp =>
          queueOpNames.put(subOp.queue.name, subOp)
        }

        outputQueueOp.subOps().foreach { subOp =>
          queueOpNames.put(subOp.queue.name, subOp)
        }

        queueOps.put(nispero.inputQueue, inputQueueOp.subOps())
        queueOps.put(nispero.outputQueue, outputQueueOp.subOps())


        inputQueueOp.subOps().zipWithIndex.foreach { case (inputSubOp, i) =>
          outputQueueOp.subOps().zipWithIndex.foreach { case (outputSubOp, j) =>
            edges += Edge(nispero.configuration.name + i + "_" + j, Node(inputSubOp.queue.name), Node(outputQueueOp.queue.name))
          }
        }
      }
      new NisperoGraph(new Graph(edges.toList), nisperoNames.toMap, queueOpNames.toMap, queueOps.toMap)
    }
  }
}


class NisperoGraph(
                    graph: Graph[String, String],
                    nisperos: Map[String, AnyNispero],
                    val queueOpNames: Map[String, AnyQueueOp],
                    val queueOps: Map[AnyQueue, List[AnyQueueOp]]) {

  val sortedQueueNames = graph.sort

  val notLeafsQueues: List[AnyQueueOp] = sortedQueueNames.filterNot(graph.out(_).isEmpty).map { node =>
    queueOpNames(node.label)
  }

//  val queueOps: List[AnyQueueOp] = {
//    sortedQueueNames.map { node =>
//      queuesOpsNames(node.label)
//    }
//  }

  //return either first not empty queue, either all not-leafs (to delete them)
  def checkQueues[E <: AnyEnvironment[E]](env: E): Try[Either[AnyQueueOp, List[AnyQueueOp]]] = Try {

    notLeafsQueues.find { queue => !queue.isEmpty.get  } match {
      case None =>  Right(notLeafsQueues)
      case Some(queueOp) => Left(queueOp)
    }
  }
}

