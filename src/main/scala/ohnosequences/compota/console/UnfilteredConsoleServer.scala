package ohnosequences.compota.console

import java.io.ByteArrayInputStream

import ohnosequences.logging.Logger
import unfiltered.Cycle
import unfiltered.netty._
import unfiltered.netty.cycle.Plan.Intent
import unfiltered.response._
import unfiltered.request.{BasicAuth, Path, Seg, GET}
import unfiltered.netty.cycle.{Plan, SynchronousExecution}

import scala.util.{Failure, Success}


trait Users {
  def auth(u: String, p: String): Boolean
}

case class AuthWithLogging(users: Users, logger: Logger) {
  def apply[A, B](intent: Cycle.Intent[A, B]) =
    Cycle.Intent[A, B] {
      case req@BasicAuth(user, pass) if users.auth(user, pass) =>
        //println(req.uri)
        logger.info(req.uri)
        Cycle.Intent.complete(intent)(req)
      case _ =>
        Unauthorized ~> WWWAuthenticate( """Basic realm="/"""")
    }
}

case class HtmlCustom(s: String) extends ComposeResponse(HtmlContent ~> ResponseString(s))

@io.netty.channel.ChannelHandler.Sharable
class ConsolePlan(users: Users, console: AnyConsole) extends Plan with Secured
                                                                  with SynchronousExecution
                                                                  with ServerErrorResponse {
  val logger = console.logger

  def intent = AuthWithLogging(users, logger) {
    case GET(Path("/")) => {
      val mainPage = console.mainHTML.mkString
        .replace("@main", console.compotaInfoPage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)
      HtmlCustom(mainPage)
    }

    case GET(Path("/undeploy")) => {
      console.sendForceUnDeployCommand("terminated from console", "terminated from console")
      ResponseString("undeploy message was sent")
    }

    case GET(Path("/errorsPage")) => {

      val mainPage = console.mainHTML.mkString
        .replace("@main", console.errorsPage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)

      HtmlCustom(mainPage)
    }


    case GET(Path("/namespacePage")) => {

      val page = console.mainHTML.mkString
        .replace("@main", console.namespacePage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)

      HtmlCustom(page)
    }

    case GET(Path(Seg("errors" :: Nil))) => {
      ResponseString(console.printErrorTable(None).toString())
    }

    case GET(Path(Seg("errors" :: lastToken :: Nil))) => {
      ResponseString(console.printErrorTable(Some(lastToken)).toString())
    }

    case GET(Path("/shutdown")) => {
      console.shutdown()
      ResponseString("ok")
    }

    case GET(Path(Seg("queue" :: queueName ::  "messages" :: Nil))) => {
      ResponseString(console.printMessages(queueName, None).toString())
    }

    case GET(Path(Seg("queue" :: queueName ::  "messages" :: lastToken :: Nil))) => {
      ResponseString(console.printMessages(queueName, Some(lastToken)).toString())
    }

    case GET(Path(Seg("logging" :: instanceId :: Nil))) => {
      ResponseString(console.printLog(instanceId).toString())
    }

    case GET(Path(Seg("logging" :: instanceId :: namespace :: Nil))) => {
      ResponseString(console.printLog(instanceId, namespace).toString())
    }


    case GET(Path(Seg("queue" :: queueName ::  "message" :: id :: Nil))) => {
      console.getMessage(queueName, id) match {
        case Failure(t) => ResponseString(t.toString)
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
      }
    }

    case GET(Path(Seg("instance" :: id :: "terminate" :: Nil))) => {
      ResponseString(console.terminateInstance(id).toString)
    }


    case GET(Path(Seg("logging" :: "raw" :: instanceId :: Nil))) => {
      console.getLogRaw(instanceId) match {
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("logging" :: "raw" :: instanceId :: namespace :: Nil))) => {
      console.getLogRaw(instanceId, namespace) match {
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
        case Failure(t) => NotFound
      }
    }


    case GET(Path(Seg("instance" :: id :: "ssh" :: Nil))) => {
      ResponseString(console.sshInstance(id).toString)
    }

    case GET(Path(Seg("instance" :: id :: "stackTrace" :: Nil))) => {
      ResponseString(console.stackTraceInstance(id).toString)
    }

    case GET(Path(Seg("error" :: "message" :: namespase :: timestamp :: instanceId ::  Nil))) => {
      console.getErrorMessage(namespase, timestamp, instanceId) match {
        case Success(s) => ResponseString(s)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("error" :: "stackTrace" :: namespase :: timestamp :: instanceId ::  Nil))) => {
      console.getErrorStackTrace(namespase, timestamp, instanceId) match {
        case Success(s) => ResponseString(s)
        case Failure(t) => NotFound
      }
    }


    case GET(Path(Seg("nispero" :: nispero :: "workers" ::  Nil))) => {
      ResponseString(console.printWorkers(nispero, None).toString())
    }

    case GET(Path(Seg("nispero" :: nispero :: "workers" ::  lastToken :: Nil))) => {
      ResponseString(console.printWorkers(nispero, Some(lastToken)).toString())
    }


    case GET(Path("/threads")) => {
      val resp = new StringBuilder

      import scala.collection.JavaConversions._

      Thread.getAllStackTraces.foreach { case (thread, sts) =>
        resp.append(thread + ":" + thread.getState + System.lineSeparator())
        sts.foreach { st =>
          resp.append("      " + st.toString + System.lineSeparator())
        }
      }
      ResponseString(resp.toString)

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


class UnfilteredConsoleServer(console: AnyConsole) {


  object users extends Users {
    override def auth(u: String, p: String): Boolean = u.equals("nispero") && p.equals(console.password)
  }


  def shutdown() {
    Runtime.getRuntime().halt(0)
  }


  def start() {

    console.logger.info("starting console server")

    try {
      val server: Server = if (console.isHTTPS) {
        unfiltered.netty.Server.https(port = 443, ssl = SslContextProvider.selfSigned(new io.netty.handler.ssl.util.SelfSignedCertificate)).handler(new ConsolePlan(users, console))
      } else {
        unfiltered.netty.Server.http(port = 80).handler(new ConsolePlan(users, console))
      }
      server.start()
      console.logger.info("console server started on port " + server.ports.head)

    } catch {
      case t: Throwable => {
        val server = if(console.isHTTPS) {
          unfiltered.netty.Server.https(443, "localhost", ssl = SslContextProvider.selfSigned(new io.netty.handler.ssl.util.SelfSignedCertificate)).handler(new ConsolePlan(users, console))
        } else {
          unfiltered.netty.Server.http(80, "localhost").handler(new ConsolePlan(users, console))
        }
        server.start()
        console.logger.info("console server started on port " + server.ports.head)

      }
    }
  }

}
