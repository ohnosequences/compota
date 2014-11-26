package ohnosequences.compota.metamanager

import ohnosequences.compota.{NisperoAux, Nispero, Compota}


trait Command {

}

case class CreateWorkerGroup(nispero: NisperoAux) extends Command
