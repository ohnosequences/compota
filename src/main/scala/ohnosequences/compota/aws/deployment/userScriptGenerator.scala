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
                  |chmod a+r log.txt
                  |
                  |aws s3 cp $jarUrl$ $jarFile$ --region eu-west-1
                  """.stripMargin +
        (testJarUrl match {
          case None => {
            mainClass match {
              case None => {
                """
                  |java -jar $jarFile$ run $component$ $name$
                  |""".stripMargin
              }
              case Some(mainClass0) => {
                """
                  |java -cp $jarFile$ $mainClass$ run $component$ $name$
                  |""".stripMargin.replace("$mainClass$", mainClass0)
              }
            }
          }
          case Some(testJar) => {
            mainClass match {
              case None => {
                """
                  |aws s3 cp $testJarUrl$ $testJarFile$ --region eu-west-1
                  |java -jar $testJarFile$ -cp $jarFile$ run $component$ $name$
                """.stripMargin
              } case Some(mainClass0) => {
                """
                  |aws s3 cp $testJarUrl$ $testJarFile$ --region eu-west-1
                  |java -cp $jarFile$:$testJarFile$ $mainClass$ run $component$ $name$
                """.stripMargin.replace("$mainClass$", mainClass0)
              }
            }
          }
      })).replace("$jarUrl$", jarUrl)
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
