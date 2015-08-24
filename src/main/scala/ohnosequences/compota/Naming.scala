package ohnosequences.compota


object Naming {
  def name(nisperon: CompotaConfiguration, nispero: NisperoConfiguration, suffix: String): String = {
    "nispero_" + nisperon.id  + "_" + nispero.name + "_" + suffix
  }

  def name(nisperon: CompotaConfiguration, suffix: String): String = {
    "nispero_" + nisperon.id  + "_" + suffix
  }

  def s3name(nisperon: CompotaConfiguration, suffix: String): String = {
    ("nispero-" + nisperon.id + "-" + suffix).replace("_", "-").toLowerCase
  }

  def s3name(nisperonId: String): String = {
    "nispero-" + nisperonId.replace("_", "-").toLowerCase
  }

  def notificationTopic(nisperon: CompotaConfiguration): String = {
    "nispero_" + nisperon.email.hashCode
  }

 }
