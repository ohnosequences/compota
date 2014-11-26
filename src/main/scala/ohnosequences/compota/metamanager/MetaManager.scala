package ohnosequences.compota.metamanager


import scala.util.{Success, Try}

//abstract manager without command queue manipulations


class MetaManager {

  def proccess(command: Command): Try[List[Command]] = {
    command match {
      case CreateWorkerGroup(nispero) => {
//        val workersGroup = nispero.nisperoConfiguration.workerGroup
//
//        logger.info("nispero " + nispero.nisperoConfiguration.name + ": generating user script")
//        val script = userScript(worker)
//
//        logger.info("nispero " + nispero.nisperoConfiguration.name + ": launching workers group")
//        val workers = workersGroup.autoScalingGroup(
//          name = nispero.nisperoConfiguration.workersGroupName,
//          defaultInstanceSpecs = nispero.nisperoConfiguration.nisperonConfiguration.defaultInstanceSpecs,
//          amiId = nispero.managerDistribution.ami.id,
//          userData = script
//        )
//
//        aws.as.createAutoScalingGroup(workers)
        Success(List[Command]())
      }
    }
  }
}
