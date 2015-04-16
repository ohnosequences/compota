package ohnosequences.compota

import ohnosequences.compota.metamanager._

import org.junit.Test
import org.junit.Assert._


class BaseCommands {

  @Test
  def serializerTests(): Unit = {

    val createNisperoWorkers = CreateNisperoWorkers(1)
    assertEquals(createNisperoWorkers, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(createNisperoWorkers).get).get)

    val deleteNisperoWorkers = DeleteNisperoWorkers(1, "reason", true)
    assertEquals(deleteNisperoWorkers, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(deleteNisperoWorkers).get).get)

    val unDeploy = UnDeploy("reason", true)
    assertEquals(unDeploy, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(unDeploy).get).get)

    val unDeployActions = UnDeployActions("reason", true)
    assertEquals(unDeployActions, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(unDeployActions).get).get)

    val reduceQueue = ReduceQueue(1, "reason")
    assertEquals(reduceQueue, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(reduceQueue).get).get)

    val deleteQueue = DeleteQueue(1, "reason", false)
    assertEquals(deleteQueue, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(deleteQueue).get).get)

    val finishCompota = FinishCompota("reason", "message")
    assertEquals(finishCompota, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(finishCompota).get).get)

    assertEquals(AddTasks, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(AddTasks).get).get)

    assertEquals(UnDeployMetaManger, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(UnDeployMetaManger).get).get)

    val commands = List(createNisperoWorkers, deleteNisperoWorkers, unDeploy,
      unDeployActions, reduceQueue, deleteQueue, finishCompota, AddTasks, UnDeployMetaManger)

    assertEquals(commands.size, commands.map { c => BaseCommandSerializer.toString(c).get}.toSet.size)


  }

}
