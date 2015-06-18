package ohnosequences.compota

import ohnosequences.compota.metamanager._

import org.junit.Test
import org.junit.Assert._


class BaseCommands {

  @Test
  def serializerTests(): Unit = {

    val createNisperoWorkers = CreateNisperoWorkers(1)
    assertEquals(createNisperoWorkers, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(createNisperoWorkers).get).get)

    val deleteNisperoWorkers = DeleteNisperoWorkers(1)
    assertEquals(deleteNisperoWorkers, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(deleteNisperoWorkers).get).get)

    assertEquals(UnDeploy, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(UnDeploy).get).get)

    val forceUnDeploy = ForceUnDeploy("reason", "message")
    assertEquals(forceUnDeploy, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(forceUnDeploy).get).get)

    assertEquals(ExecuteUnDeployActions, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(ExecuteUnDeployActions).get).get)


    val reduceQueue = ReduceQueue(1)
    assertEquals(reduceQueue, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(reduceQueue).get).get)

    val deleteQueue = DeleteQueue(1)
    assertEquals(deleteQueue, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(deleteQueue).get).get)

    val finishCompota = FinishCompota("reason", "message")
    assertEquals(finishCompota, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(finishCompota).get).get)

    val sendNotification = SendNotification("subject", "message")
    assertEquals(sendNotification, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(sendNotification).get).get)

    assertEquals(AddTasks, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(AddTasks).get).get)

    val unDeployMetaManger = UnDeployMetaManger("reason", "message")
    assertEquals(unDeployMetaManger, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(unDeployMetaManger).get).get)

    val prepareUnDeployActions = PrepareUnDeployActions(true)
    assertEquals(prepareUnDeployActions, BaseCommandSerializer.fromString(BaseCommandSerializer.toString(prepareUnDeployActions).get).get)

    val commands = List(createNisperoWorkers, deleteNisperoWorkers, UnDeploy,
      forceUnDeploy, reduceQueue, deleteQueue, finishCompota, AddTasks, unDeployMetaManger, prepareUnDeployActions, ExecuteUnDeployActions, sendNotification)

    assertEquals(commands.size, commands.map { c => BaseCommandSerializer.toString(c).get}.toSet.size)


  }

}
