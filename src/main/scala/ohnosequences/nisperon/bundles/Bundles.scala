package ohnosequences.nisperon.bundles

import ohnosequences.statika._, bundles._
import ohnosequences.statika.aws._, api._, amazonLinuxAMIs._
import ohnosequences.awstools.regions.Region._


import ohnosequences.nisperon._
import org.clapper.avsl.Logger
import ohnosequences.nisperon.queues.SQSQueue

trait AnyInstructionsBundle extends AnyModule {
  type BundleInstructions <: InstructionsAux
  val instructions: BundleInstructions
  //todo make configurable
  override val bundleDependencies: List[AnyBundle] = List()
}

abstract class InstructionsBundle[I <: InstructionsAux](val instructions: I) extends AnyInstructionsBundle {
  override type BundleInstructions = I
}

trait AnyWorkerBundle extends AnyBundle {
  type WorkerInstructionsBundle <: InstructionsBundle
  val instructionsBundle: WorkerInstructionsBundle
}

abstract class WorkerBundle[I <: InstructionsBundle](val instructionsBundle: I) extends AnyWorkerBundle {
  override type WorkerInstructionsBundle = I
  override val bundleDependencies: List[AnyBundle] = instructionsBundle.bundleDependencies
}

abstract class ManagerBundle extends AnyModule {
  override val bundleDependencies: List[AnyBundle] = List()
}

abstract class NisperoCompatible[WB <: AnyWorkerBundle](workerBundle: WB, metadata: AnyArtifactMetadata) extends Compatible(
  amzn_ami_pv_64bit(Ireland)(1),
  workerBundle,
  metadata
)


abstract class ManagerCompatible[MB <: ManagerBundle](managerBundle: MB, metadata: AnyArtifactMetadata) extends Compatible(
  amzn_ami_pv_64bit(Ireland)(1),
  managerBundle,
  metadata
)

//abstract class ManagerDistribution[W <: WorkerBundleAux, T <: HList : towerFor[∅]#is](val worker: W) extends ManagerDistributionAux {
//
//  type WA = W
//
//  type DepsTower = T
//  val  depsTower = deps.tower
//}

//
//trait NisperoDistributionAux extends AnyAWSDistribution {
//  type MA <: ManagerDistributionAux
//  val manager: MA
//
//  type Deps = ∅
//  val deps = ∅
//
//  type Members = MA :~: ∅
//  val members = manager :~: ∅
//
//  type Metadata = NisperonMetadata
//
//  type AMI = NisperonAMI.type
//  val ami = NisperonAMI
//}
//





//abstract class NisperoDistribution[M <: ManagerDistributionAux, T <: HList : towerFor[∅]#is](val manager: M) extends NisperoDistributionAux {
//  type MA =  M
//
//  type DepsTower = T
//  val  depsTower = deps.tower
//
//
//  override def install[Dist <: AnyDistribution](distribution: Dist): InstallResults = {
//    success("NisperoDistribution finished")
//  }
//}



//class WhateverBundle[T <: HList : towerFor[∅]#is](nisperon: Nisperon, component: String, name: String) extends AnyAWSDistribution {
//
//  type Deps = ∅
//  val deps = ∅
//
//  type DepsTower = T
//  val  depsTower = deps.tower
//
//
//  type Members = WhateverBundle[T] :~: ∅
//  val members = this :~: ∅
//
//  type Metadata = NisperonMetadata
//
//  type AMI = NisperonAMI.type
//  val ami = NisperonAMI
//
//  val metadata = nisperon.nisperonConfiguration.metadataBuilder.build(component, name, nisperon.nisperonConfiguration.workingDir)
//
//  override def install[Dist <: AnyDistribution](distribution: Dist): InstallResults = {
//    success("WhateverBundle finished")
//
//  }
//}


