package ohnosequences.compota.graphs

import scala.collection.mutable.ListBuffer

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

  def sort: List[Node[N]] =  {
    var arcFree = new Graph(edges.filterNot { edge =>
      edge.source.equals(edge.target)
    })

    val result = ListBuffer[Node[N]]()
    var s: List[Node[N]] = nodes.toList.filter(arcFree.in(_).isEmpty)
    //println(s)
    while(!s.isEmpty) {
      val n = s.head
      s = s.tail
      result += n

      // println("analyzing node: " + n + " in: " + arcFree.out(n))
      arcFree.out(n).foreach { e =>
        arcFree = arcFree.remove(e)
        if(!arcFree.nodes.contains(e.target) || arcFree.in(e.target).isEmpty) {
          s = e.target :: s
        }
      }
    }

    if(!arcFree.edges.isEmpty) {
      // println("oioioi")
    }
    result.toList
  }

}