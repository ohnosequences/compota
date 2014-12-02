package ohnosequences.compota.graphs


import ohnosequences.compota._
import ohnosequences.compota.queues._

// TODO: ???
import scala.collection.mutable.HashMap

//todo fix product queue
class NisperoGraph(nisperos: Map[String, AnyNispero]) {

  val queues = {
    val r = new HashMap[String, QueueAux]()

//    nisperos.values.foreach { nispero =>
//      r ++= ProductQueue.flatQueue(nispero.inputQueue).map{ q=>
//        q.name -> q
//      }
//      r ++= ProductQueue.flatQueue(nispero.outputQueue).map{ q=>
//        q.name -> q
//      }
//    }
    r
  }

    val edges = nisperos.values.toList.flatMap { nispero =>
      for {
        i <- Seq(nispero.inputQueue)
        o <- Seq(nispero.outputQueue)
      } yield Edge(
        label = nispero.name,
        source = Node(i.name),
        target = Node(o.name)
      )
    }

//  val edges = nisperos.values.toList.flatMap { nispero =>
//    for {
//      i <- ProductQueue.flatQueue(nispero.inputQueue)
//      o <- ProductQueue.flatQueue(nispero.outputQueue)
//    } yield Edge(
//      label = nispero.name,
//      source = Node(i.name),
//      target = Node(o.name)
//    )
//  }

  val graph: Graph[String, String] = new Graph(edges)



  //return either not leafs queues all (to delete them
  def checkQueues(): Either[AnyQueue, List[AnyQueue]] = {
    val sorted = graph.sort
    println(sorted)


    val notLeafsQueues = sorted.filterNot(graph.out(_).isEmpty).map { node =>
      queues(node.label)
    }

    notLeafsQueues.find { queue =>
    //  !queue.isEmpty
      true
    } match {
      case None => println("all queues are empty"); Right(notLeafsQueues)
      case Some(queue) => println("queue " + queue.name + " isn't empty"); Left(queue)
    }


  }

}

