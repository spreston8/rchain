package coop.rchain.casper.util.rholang

import com.google.protobuf.ByteString

import coop.rchain.catscontrib.TaskContrib._
import coop.rchain.models.Par
import coop.rchain.rholang.interpreter.Runtime
import coop.rchain.rholang.interpreter.storage.StoragePrinter
import coop.rchain.rspace.Blake2b256Hash

import monix.execution.Scheduler

import scala.concurrent.SyncVar
import scala.util.{Failure, Success, Try}

//runtime is a SyncVar for thread-safety, as all checkpoints share the same "hot store"
class Checkpoint private (val hash: ByteString, runtime: SyncVar[Runtime]) {

  def updated(terms: List[Par])(implicit scheduler: Scheduler): Either[Throwable, Checkpoint] = {
    val hot   = getHot()
    val error = eval(terms, hot)
    val newHash = error.fold[Either[Throwable, ByteString]](
      Right(ByteString.copyFrom(hot.space.getCheckpoint().bytes.toArray)))(Left(_))
    runtime.put(hot)

    newHash.map(new Checkpoint(_, runtime))
  }

  def storageRepr: String = {
    val hot    = getHot()
    val result = StoragePrinter.prettyPrint(hot.space.store)
    runtime.put(hot)
    result
  }

  private def getHot(): Runtime = {
    val hot       = runtime.take()
    val blakeHash = Blake2b256Hash.fromByteArray(hash.toByteArray)
    Try(hot.space.reset(blakeHash)) match {
      case Success(_) => hot
      case Failure(ex) =>
        runtime.put(hot)
        throw ex
    }
  }

  private def eval(terms: List[Par], hot: Runtime)(
      implicit scheduler: Scheduler): Option[Throwable] =
    terms match {
      case term :: rest =>
        Try(hot.reducer.inj(term).unsafeRunSync) match {
          case Success(_)  => eval(rest, hot)
          case Failure(ex) => Some(ex)
        }

      case Nil => None
    }
}

object Checkpoint {
  def fromRuntime(runtime: SyncVar[Runtime]): Checkpoint = {
    val hot  = runtime.take()
    val hash = ByteString.copyFrom(hot.space.getCheckpoint().bytes.toArray)
    runtime.put(hot)

    new Checkpoint(hash, runtime)
  }
}
