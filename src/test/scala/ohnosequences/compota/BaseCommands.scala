package ohnosequences.compota

import ohnosequences.compota.metamanager._

import org.junit.Test
import org.junit.Assert._


class BaseCommands {

  @Test
  def serializerTests(): Unit = {

    val createNisperoWorkers = CreateNisperoWorkers(1)
    assertEquals(createNisperoWorkers, CommandSerializer.fromString(CommandSerializer.toString(createNisperoWorkers).get).get)

    val deleteNisperoWorkers = DeleteNisperoWorkers(1, "reason", true)
    assertEquals(deleteNisperoWorkers, CommandSerializer.fromString(CommandSerializer.toString(deleteNisperoWorkers).get).get)

    val unDeploy = UnDeploy("reason", true)
    assertEquals(unDeploy, CommandSerializer.fromString(CommandSerializer.toString(unDeploy).get).get)

    val unDeployActions = UnDeployActions("reason", true)
    assertEquals(unDeployActions, CommandSerializer.fromString(CommandSerializer.toString(unDeployActions).get).get)

    val reduceQueue = ReduceQueue(1, "reason")
    assertEquals(reduceQueue, CommandSerializer.fromString(CommandSerializer.toString(reduceQueue).get).get)

    val deleteQueue = DeleteQueue(1, "reason", false)
    assertEquals(deleteQueue, CommandSerializer.fromString(CommandSerializer.toString(deleteQueue).get).get)

    val finishCompota = FinishCompota("reason", "message")
    assertEquals(finishCompota, CommandSerializer.fromString(CommandSerializer.toString(finishCompota).get).get)

    assertEquals(AddTasks, CommandSerializer.fromString(CommandSerializer.toString(AddTasks).get).get)

    assertEquals(UnDeployMetaManger, CommandSerializer.fromString(CommandSerializer.toString(UnDeployMetaManger).get).get)

    val commands = List(createNisperoWorkers, deleteNisperoWorkers, unDeploy,
      unDeployActions, reduceQueue, deleteQueue, finishCompota, AddTasks, UnDeployMetaManger)

    assertEquals(commands.size, commands.map { c => CommandSerializer.toString(c).get}.toSet.size)


  }

}
