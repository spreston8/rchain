package coop.rchain.blockstorage

import cats._
import cats.effect.concurrent.Ref
import cats.implicits._
import coop.rchain.blockstorage.BlockStore.BlockHash
import coop.rchain.casper.protocol.BlockMessage
import coop.rchain.metrics.Metrics
import coop.rchain.shared.SyncVarOps

import scala.concurrent.SyncVar
import scala.language.higherKinds

class InMemBlockStore[F[_], E] private ()(implicit
                                          monadF: Monad[F],
                                          refF: Ref[F, Map[BlockHash, BlockMessage]],
                                          metricsF: Metrics[F])
    extends BlockStore[F] {

  //implicit val monad: Monad[F] = monadF

  def put(blockHash: BlockHash, blockMessage: BlockMessage): F[Unit] =
    for {
      _ <- metricsF.incrementCounter("block-store-put")
      _ <- refF.update(_.updated(blockHash, blockMessage))
    } yield ()

  def get(blockHash: BlockHash): F[Option[BlockMessage]] =
    for {
      _   <- metricsF.incrementCounter("block-store-get")
      ret <- refF.get
    } yield ret.get(blockHash)

  //TODO mark as deprecated and remove when casper code no longer needs it
  def asMap(): F[Map[BlockHash, BlockMessage]] = ???
  /*
    for {
      _ <- metricsF.incrementCounter("block-store-as-map")
      ret <- bracketF.bracket(applicative.pure(stateRef.take()))(state => applicative.pure(state))(
              state => applicative.pure(stateRef.put(state)))
    } yield ret

   */
  def put(f: => (BlockHash, BlockMessage)): F[Unit] = ??? /*
    for {
      _ <- metricsF.incrementCounter("block-store-put")
      ret <- bracketF.bracket(applicative.pure(stateRef.take())) { state =>
              val (blockHash, blockMessage) = f
              applicative.pure(stateRef.put(state.updated(blockHash, blockMessage)))
            }(_ => applicative.pure(()))
    } yield ret
 */
}

object InMemBlockStore {
  def create[F[_], E](implicit
                      monadF: Monad[F],
                      refF: Ref[F, Map[BlockHash, BlockMessage]],
                      metricsF: Metrics[F]): BlockStore[F] =
    new InMemBlockStore()

  /*
  type ExceptionalBracket[F[_]] = Bracket[F, Exception]

  def inMemInstanceEff[F[_], E](implicit
                                bracketF: Bracket[F, E],
                                metricsF: Metrics[F]): BlockStore[F] =
    InMemBlockStore.create[F, E](bracketF, metricsF)

  def inMemInstanceId: BlockStore[Id] = {
    import coop.rchain.metrics.Metrics.MetricsNOP
    implicit val metrics: Metrics[Id] = new MetricsNOP[Id]()(bracketId)
    InMemBlockStore.create(bracketId, metrics)
  }

  def bracketId: Bracket[Id, Exception] =
    new Bracket[Id, Exception] {
      def pure[A](x: A): cats.Id[A] = implicitly[Applicative[Id]].pure(x)

      // Members declared in cats.ApplicativeError
      def handleErrorWith[A](fa: cats.Id[A])(f: Exception => cats.Id[A]): cats.Id[A] = ???
      def raiseError[A](e: Exception): cats.Id[A]                                    = ???

      // Members declared in cats.FlatMap
      def flatMap[A, B](fa: cats.Id[A])(f: A => cats.Id[B]): cats.Id[B] =
        implicitly[FlatMap[Id]].flatMap(fa)(f)
      def tailRecM[A, B](a: A)(f: A => cats.Id[Either[A, B]]): cats.Id[B] =
        implicitly[FlatMap[Id]].tailRecM(a)(f)

      def bracketCase[A, B](acquire: A)(use: A => B)(
          release: (A, ExitCase[Exception]) => Unit): B = {
        val state = acquire
        try {
          use(state)
        } finally {
          //FIXME add exception handling
          release(acquire, ExitCase.Completed)
        }
      }
    }
 */
}
