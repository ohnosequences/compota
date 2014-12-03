package ohnosequences.compota.aws.metamanager

import ohnosequences.compota.{AnyNispero, Nispero, Compota}


trait Command {

}

case class CreateWorkerGroup(nispero: AnyNispero) extends Command
