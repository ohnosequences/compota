package ohnosequences.compota.graphs

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

case class Node[N](label: N)

case class Edge[E, N](label: E, source: Node[N], target: Node[N])


class Graph[N, E](val edges: List[Edge[E, N]]) {

  override def toString() = {
    "nodes: " + nodes + System.lineSeparator() + "edges: " + edges
  }

  val nodes: Set[Node[N]] = {
    edges.toSet.flatMap { edge: Edge[E, N] =>
      Set(edge.source, edge.target)
    }
  }

  def remove(edge: Edge[E, N]): Graph[N, E] = {
    new Graph(edges.filterNot(_.equals(edge)))
  }

  def out(node: Node[N]): Set[Edge[E, N]] = {
    edges.filter(_.source.equals(node)).toSet
  }

  def in(node: Node[N]): Set[Edge[E, N]] = {
    edges.filter(_.target.equals(node)).toSet
  }

  def arcFree: Graph[N, E] = new Graph(edges.filterNot { edge =>
    edge.source.equals(edge.target)
  })

  def sort(): Try[List[Node[N]]] = {
    val arcFree = new Graph(edges.filterNot { edge =>
      edge.source.equals(edge.target)
    })

    val visitedEdges = new mutable.HashSet[Edge[E, N]]()

    val result = new mutable.ListBuffer[Node[N]]()

    val sources = new mutable.ArrayBuffer[Node[N]]()
    sources ++= nodes.filter(arcFree.in(_).isEmpty)

    while (sources.nonEmpty) {
      val n = sources.remove(sources.size - 1)
      result += n
      arcFree.out(n).foreach { e =>
        if (!visitedEdges.contains(e)) {
          visitedEdges.add(e)
          if (arcFree.in(e.target).forall(visitedEdges.contains)) {
            sources += e.target
          }
        }
      }
    }
    if (arcFree.edges.forall(visitedEdges.contains)) {
      Success(result.toList)
    } else {
      Failure(new Error("unable to sort graph unvisited edges: " + edges.filterNot(visitedEdges.contains) + " edges: " + arcFree.edges))
    }
  }

}