package ohnosequences.compota.graph

import ohnosequences.compota.graphs.{Edge, Node, Graph}
import org.junit.Test
import org.junit.Assert._

import scala.util.Success

class GraphTests {

  @Test
  def graphTests(): Unit = {
    val emptyGraph = new Graph[String, String](List())
    assertEquals("empty graph", Success(List[Node[String]]()), emptyGraph.sort())

    val oneEdgeGraph = new Graph(List(Edge("l", Node("1"), Node("2"))))
    assertEquals("one edge", Success(List(Node("1"), Node("2"))), oneEdgeGraph.sort())

    val oneEdgeOneArcGraph = new Graph(List(
      Edge("l", Node("1"), Node("2")),
      Edge("arc", Node("2"), Node("2"))
    ))
    assertEquals("one edge one arc", Success(List(Node("1"), Node("2"))), oneEdgeOneArcGraph.sort())

    val loopGraph = new Graph(List(
      Edge("l", Node("1"), Node("2")),
      Edge("2", Node("2"), Node("1"))
    ))
    assertEquals("loop graph", true, loopGraph.sort().isFailure)

    val disconnectedGraph = new Graph(List(
      Edge("l", Node("1"), Node("2")),
      Edge("2", Node("3"), Node("4"))
    ))
    assertEquals("disconnected Graph", Success(List(Node("3"), Node("4"), Node("1"), Node("2"))), disconnectedGraph.sort())

    val graph1 = new Graph(List(
      Edge("l", Node("1"), Node("2")),
      Edge("2", Node("2"), Node("3")),
      Edge("3", Node("1"), Node("3")),
      Edge("4", Node("3"), Node("4"))
    ))
    assertEquals("graph1", Success(List(Node("1"), Node("2"), Node("3"), Node("4"))), graph1.sort())

    val graph2 = new Graph(List(
      Edge("l", Node("1"), Node("3")),
      Edge("2", Node("2"), Node("3")),
      Edge("3", Node("3"), Node("4")),
      Edge("4", Node("1"), Node("4")),
      Edge("5", Node("1"), Node("5"))
    ))
    assertEquals("graph2", Success(List(Node("2"), Node("1"), Node("5"), Node("3"), Node("4"))), graph2.sort())
  }

}
