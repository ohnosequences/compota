package ohnosequences.compota.local

import java.util.concurrent.{ExecutorService, Executor}

import ohnosequences.logging.Logger

import scala.concurrent.ExecutionContext


class LocalContext(val executor: ExecutorService, val logger: Logger) {
}
