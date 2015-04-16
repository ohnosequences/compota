package ohnosequences.compota.aws.deployment


import ohnosequences.awstools.s3.ObjectAddress

object userScriptGenerator {
  def generate(nispero: String, component: String, jarUrl: String, workingDir: String): String = {


      val raw = """
                  |#!/bin/sh
                  |mkdir -p $workingDir$
                  |cd $workingDir$
                  |
                  |exec &> log.txt
                  |yum install java-1.7.0-openjdk.x86_64 -y
                  |chmod a+r log.txt
                  |alternatives --install /usr/bin/java java /usr/lib/jvm/jre-1.7.0-openjdk.x86_64/bin/java 20000
                  |alternatives --auto java                  |
                  |
                  |aws s3 cp s3://$jarUrl$ $workingDir$/$jarFile$ --region eu-west-1
                  |java -jar $workingDir$/$jarFile$ $component$ $name$
                  |
                """.stripMargin
        .replace("jarUrl", jarUrl)
        .replace("$jarFile$", getFileName(jarUrl))
        .replace("$component$", component)
        .replace("$name$", nispero)
        .replace("$workingDir$", workingDir)
    fixLineEndings(raw)
  }

  def fixLineEndings(s: String): String = s.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n")

  def getFileName(s: String) = s.substring(s.lastIndexOf("/") + 1)

}
