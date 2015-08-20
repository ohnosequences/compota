package ohnosequences.compota.bundles

import ohnosequences.awstools.regions.Region

import ohnosequences.statika.aws.amazonLinuxAMIs.{amiMap, AmazonLinuxAMI}
import ohnosequences.statika.aws.api.{Architecture, Virtualization}

class NisperoAmi(val region: Region, val virt: Virtualization) extends AmazonLinuxAMI (
  id = amiMap.id(region, virt),
  amiVersion = "2015.03"
) {
  override val arch: Architecture = Architecture.x64
  override val workingDir: String = "/media/ephemeral0/applicator"
  override val javaHeap: Int = 1

}
