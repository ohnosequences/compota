package ohnosequences.compota.console

import java.io.ByteArrayInputStream

import unfiltered.Cycle
import unfiltered.netty.Https
import unfiltered.response._
import unfiltered.request.{BasicAuth, Path, Seg, GET}
import unfiltered.netty.cycle.{Plan, SynchronousExecution}
import unfiltered.netty.{Secured, ServerErrorResponse}

import scala.util.{Failure, Success}


trait Users {
  def auth(u: String, p: String): Boolean
}

case class Auth(users: Users) {
  def apply[A, B](intent: Cycle.Intent[A, B]) =
    Cycle.Intent[A, B] {
      case req@BasicAuth(user, pass) if users.auth(user, pass) =>
        println(req.uri)
        Cycle.Intent.complete(intent)(req)
      case _ =>
        Unauthorized ~> WWWAuthenticate( """Basic realm="/"""")
    }
}

case class HtmlCustom(s: String) extends ComposeResponse(HtmlContent ~> ResponseString(s))

class ConsolePlan(users: Users, console: Console) extends Plan with Secured // also catches netty Ssl errors
                                                                             with SynchronousExecution
                                                                             with ServerErrorResponse {
  val logger = console.logger

  def intent = Auth(users) {
    case GET(Path("/")) => {
      val mainPage = console.mainHTML.mkString
        .replace("@main", console.compotaInfoPage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)
      HtmlCustom(mainPage)
    }

    case GET(Path("/undeploy")) => {
      console.sendUndeployCommand("adhoc", force = true)
      ResponseString("undeploy message was sent")
    }

    case GET(Path("/errors")) => {

      val mainPage = console.mainHTML.mkString
        .replace("@main", console.errorsPage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)

      HtmlCustom(mainPage)
    }

    case GET(Path(Seg("failures" :: Nil))) => {
      ResponseString(console.listErrors(None).toString())
    }

    case GET(Path(Seg("failures" :: lastHash :: lastRange :: Nil))) => {
      ResponseString(console.listErrors(Some((lastHash, lastRange))).toString())
    }

    case GET(Path("/shutdown")) => {
      console.shutdown()
      ResponseString("ok")
    }

    case GET(Path(Seg("queue" :: queueName ::  "messages" :: Nil))) => {
      ResponseString(console.listMessages(queueName, None).toString())
    }

    case GET(Path(Seg("queue" :: queueName ::  "messages" :: lastKey :: Nil))) => {
      ResponseString(console.listMessages(queueName, Some(lastKey)).toString())
    }

    case GET(Path(Seg("instanceLog" :: instanceId :: Nil))) => {
      console.getInstanceLog(instanceId) match {
        case Some(Left(url)) => Redirect(url.toString)
        case Some(Right(log)) => ResponseString(log)
        case None => NotFound
      }
    }

    case GET(Path(Seg("log" :: id :: Nil))) => {
      console.getTaskLog(id) match {
        case Some(Left(url)) => Redirect(url.toString)
        case Some(Right(log)) => ResponseString(log)
        case None => NotFound
      }
    }


    case GET(Path(Seg("queue" :: queueName ::  "message" :: id :: Nil))) => {
      console.getMessage(queueName, id) match {
        case None => NotFound
        case Some(Left(url)) => Redirect(url.toString)
        case Some(Right(log)) => ResponseString(log)
      }
    }

    case GET(Path(Seg("terminate" :: id ::  Nil))) => {
      console.terminateInstance(id) match {
        case Failure(t) => {
          ResponseString( """<div class="alert alert-success">$error$</div>""".replace("$error$", t.toString))
        }
        case _ => {
          ResponseString( """<div class="alert alert-danger">terminated</div>""")
        }
      }
    }

    case GET(Path(Seg("ssh" :: id ::  Nil))) => {
      console.sshInstance(id) match {
        case Failure(t) => {
          ResponseString( """<div class="alert alert-success">$error$</div>""".replace("$error$", t.toString))
        }
        case _ => {
          ResponseString( """<div class="alert alert-danger">terminated</div>""")
        }
      }
    }

    case GET(Path(Seg("nispero" :: nispero :: "workerInstances" ::  Nil))) => {
      ResponseString(console.workersInfo(nispero).toString())
    }


    case GET(Path(Seg("nispero" :: nispero :: Nil))) => {

      val mainPage = console.mainHTML.mkString
        .replace("@main", console.nisperoInfoPage(nispero).toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)
        .replace("$nispero$", nispero)

      HtmlCustom(mainPage)
    }

    case GET(Path("/main.css")) => {
     // val main = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/console/main.css")).mkString
      CssContent ~> ResponseString(console.mainCSS)
    }
  }

}


class UnfilteredConsoleServer(console: Console) {


  object users extends Users {
    override def auth(u: String, p: String): Boolean = u.equals("nispero") && p.equals(console.password)
  }


  def shutdown() {
    Runtime.getRuntime().halt(0)
  }


  def start() {
    import scala.sys.process._
    val keyConf = console.sshConfigTemplate.replace("$password$", console.password)
    val is = new ByteArrayInputStream(keyConf.getBytes("UTF-8"))
    try {
      ("keytool -keystore keystore -alias netty  -genkey -keyalg RSA -storepass $password$".replace("$password$", console.password) #< is).!
    } catch {
      case t: Throwable =>
      ("keytool7 -keystore keystore -alias netty  -genkey -keyalg RSA -storepass $password$".replace("$password$", console.password) #< is).!
    }

    console.logger.info("starting console server")
    System.setProperty("netty.ssl.keyStore", "keystore")
    System.setProperty("netty.ssl.keyStorePassword", console.password)
    try {
      Https(443).handler(new ConsolePlan(users, console)).start()
    } catch {
      case t: Throwable => {
        println("trying to bind to localhost")
        Https(443, "localhost").handler(new ConsolePlan(users, console)).start()
      }
    }
  }

}
