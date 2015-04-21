package ohnosequences.compota.aws.deployment


import ohnosequences.awstools.s3.ObjectAddress

object userScriptGenerator {
  def generate(nispero: String, component: String, jarUrl: String, testJarUrl: Option[String], mainClass: Option[String], workingDir: String): String = {

      //todo check cp
      val raw = ("""
                  |#!/bin/sh
                  |mkdir -p $workingDir$
                  |cd $workingDir$
                  |
                  |exec &> log.txt
                  |yum install java-1.7.0-openjdk.x86_64 -y
                  |chmod a+r log.txt
                  |alternatives --install /usr/bin/java java /usr/lib/jvm/jre-1.7.0-openjdk.x86_64/bin/java 20000
                  |alternatives --auto java
                  |
                  |aws s3 cp s3://$jarUrl$ $workingDir$/$jarFile$ --region eu-west-1
                  """.stripMargin +
        (testJarUrl match {
          case None => {
            mainClass match {
              case None => {
                """
                  |java -jar $workingDir$/$jarFile$ $component$ $name$
                  |""".stripMargin
              }
              case Some(mainClass0) => {
                """
                  |java -cp $workingDir$/$jarFile$ $mainClass$ $component$ $name$
                  |""".stripMargin.replace("$mainClass$", mainClass0)
              }
            }
          }
          case Some(testJar) => {
            mainClass match {
              case None => {
                """
                  |aws s3 cp s3://$testJarUrl$ $workingDir$/$testJarFile$ --region eu-west-1
                  |java -jar $workingDir$/$testJarFile$ -cp $workingDir$/$jarFile$ $component$ $name$
                """.stripMargin
              } case Some(mainClass0) => {
                """
                  |aws s3 cp s3://$testJarUrl$ $workingDir$/$testJarFile$ --region eu-west-1
                  |java -cp $workingDir$/$jarFile$;$workingDir$/$testJarFile$ $mainClass$ $component$ $name$
                """.stripMargin.replace("$mainClass$", mainClass0)
              }
            }
          }
      })).replace("jarUrl", jarUrl)
        .replace("$jarFile$", getFileName(jarUrl))
        .replace("$component$", component)
        .replace("$testJarUrl$", testJarUrl.getOrElse(""))
        .replace("$testJarFile$", testJarUrl.map(getFileName).getOrElse(""))
        .replace("$name$", nispero)
        .replace("$workingDir$", workingDir)
    fixLineEndings(raw)
  }

  def fixLineEndings(s: String): String = s.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n")

  def getFileName(s: String) = s.substring(s.lastIndexOf("/") + 1)

}
