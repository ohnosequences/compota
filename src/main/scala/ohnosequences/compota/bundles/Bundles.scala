package ohnosequences.compota.bundles

import ohnosequences.compota._

import ohnosequences.awstools.regions.Region._
import ohnosequences.statika.aws.amazonLinuxAMIs.amzn_ami_64bit
import ohnosequences.statika.aws.api.Virtualization
import ohnosequences.statika.bundles.{Compatible, AnyArtifactMetadata, AnyBundle, AnyModule}


trait AnyInstructionsBundle extends AnyModule {
  type BundleInstructions <: InstructionsAux
  val instructions: BundleInstructions
  override val bundleDependencies: List[AnyBundle] = List()
}

abstract class InstructionsBundle[I <: InstructionsAux](val instructions: I) extends AnyInstructionsBundle {
  override type BundleInstructions = I
}

trait AnyWorkerBundle extends AnyBundle {
  type WorkerInstructionsBundle <: AnyInstructionsBundle
  val instructionsBundle: WorkerInstructionsBundle
}

abstract class WorkerBundle[I <: AnyInstructionsBundle](val instructionsBundle: I) extends AnyWorkerBundle {
  override type WorkerInstructionsBundle = I
  override val bundleDependencies: List[AnyBundle] = instructionsBundle.bundleDependencies
}

abstract class WorkerCompatible[WB <: AnyWorkerBundle](workerBundle: WB, metadata: AnyArtifactMetadata) extends Compatible(
  amzn_ami_64bit(Ireland, Virtualization.HVM)(1),
  workerBundle,
  metadata
)

abstract class ManagerBundle extends AnyBundle {
  override val bundleDependencies: List[AnyBundle] = List()
}

abstract class ManagerCompatible[MB <: ManagerBundle](managerBundle: MB, metadata: AnyArtifactMetadata) extends Compatible(
  amzn_ami_64bit(Ireland, Virtualization.HVM)(1),
  managerBundle,
  metadata
)

abstract class MetaManagerBundle extends AnyBundle {
  override val bundleDependencies: List[AnyBundle] = List()
}

abstract class MetaManagerCompatible[MMB <: MetaManagerBundle](metaManagerBundle: MMB, metadata: AnyArtifactMetadata) extends Compatible(
  amzn_ami_64bit(Ireland, Virtualization.HVM)(1),
  metaManagerBundle,
  metadata
)
