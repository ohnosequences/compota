package ohnosequences.compota.console

import java.io.ByteArrayInputStream

import unfiltered.Cycle
import unfiltered.netty.{SslContextProvider, Https, Secured, ServerErrorResponse}
import unfiltered.response._
import unfiltered.request.{BasicAuth, Path, Seg, GET}
import unfiltered.netty.cycle.{Plan, SynchronousExecution}

import scala.util.{Failure, Success}


trait Users {
  def auth(u: String, p: String): Boolean
}

case class Auth(users: Users) {
  def apply[A, B](intent: Cycle.Intent[A, B]) =
    Cycle.Intent[A, B] {
      case req@BasicAuth(user, pass) if users.auth(user, pass) =>
        //println(req.uri)
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

    case GET(Path(Seg("logging" :: "instance" :: instanceId :: Nil))) => {
      console.getInstanceLog(instanceId) match {
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("logging" :: "namespace" :: namespace :: Nil))) => {
      console.getNamespaceLog(namespace) match {
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
        case Failure(t) => NotFound
      }
    }


    case GET(Path(Seg("queue" :: queueName ::  "message" :: id :: Nil))) => {
      console.getMessage(queueName, id) match {
        case None => NotFound
        case Some(Left(url)) => Redirect(url.toString)
        case Some(Right(log)) => ResponseString(log)
      }
    }

    case GET(Path(Seg("instance" :: id :: "terminate" :: Nil))) => {
      console.terminateInstance(id) match {
        case Failure(t) => {
          ResponseString( """<div class="alert alert-danger">$error$</div>""".replace("$error$", t.toString))
        }
        case _ => {
          ResponseString( """<div class="alert alert-success">terminated</div>""")
        }
      }
    }

    case GET(Path(Seg("instance" :: id :: "ssh" :: Nil))) => {
      console.sshInstance(id) match {
        case Failure(t) => {
          ResponseString( """<div class="alert alert-danger">$error$</div>""".replace("$error$", t.toString))
        }
        case Success(s) => {
          ResponseString( """<div class="alert alert-success">$result$</div>""".replace("result", s))
        }
      }
    }

    case GET(Path(Seg("instance" :: id :: "stackTrace" :: Nil))) => {
      console.printInstanceStackTrace(id) match {
        case Failure(t) => {
          ResponseString( """<div class="alert alert-danger">$error$</div>""".replace("$error$", t.toString))
        }
        case Success(s) => {
          ResponseString( """<div class="alert alert-success">$result$</div>""".replace("result", s))
        }
      }
    }

    case GET(Path(Seg("error" :: "message" :: namespase :: timestamp :: instanceId ::  Nil))) => {
      console.printErrorMessage(namespase, timestamp, instanceId) match {
        case Success(s) => ResponseString(s)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("error" :: "stackTrace" :: namespase :: timestamp :: instanceId ::  Nil))) => {
      console.printErrorStackTrace(namespase, timestamp, instanceId) match {
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
   // import scala.sys.process._
   // val keyConf = console.sshConfigTemplate.replace("$password$", console.password)
   // val is = new ByteArrayInputStream(keyConf.getBytes("UTF-8"))
//    try {
//      ("keytool -keystore keystore -alias netty  -genkey -keyalg RSA -storepass $password$".replace("$password$", console.password) #< is).!
//    } catch {
//      case t: Throwable =>
//      ("keytool7 -keystore keystore -alias netty  -genkey -keyalg RSA -storepass $password$".replace("$password$", console.password) #< is).!
//    }

    console.logger.info("starting console server")
  //  System.setProperty("netty.ssl.keyStore", "keystore")
   // System.setProperty("netty.ssl.keyStorePassword", console.password)


    try {
      //io.netty.handler.ssl.util.SelfSignedCertificate
      unfiltered.netty.Server.https(port = 443, ssl = SslContextProvider.selfSigned(new io.netty.handler.ssl.util.SelfSignedCertificate)).handler(new ConsolePlan(users, console)).run {s =>
        console.logger.info("started: " + s.portBindings.head.url)
      }
    } catch {
      case t: Throwable => {
        println("trying to bind to localhost")
        unfiltered.netty.Server.https(443, "localhost", ssl = SslContextProvider.selfSigned(new io.netty.handler.ssl.util.SelfSignedCertificate)).handler(new ConsolePlan(users, console)).run {
          s =>         console.logger.info("started: " + s.portBindings.head.url)
        }

        // Https(443, "localhost").handler(new ConsolePlan(users, console)).start()
      }
    }
  }

}
