package coop.rchain.blockstorage

import cats.{Monad, MonadError}
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import com.google.protobuf.ByteString
import coop.rchain.casper.protocol.BlockMessage
import coop.rchain.metrics.Metrics

import scala.language.higherKinds

trait BlockStore[F[_]] {
  import BlockStore.BlockHash

  //implicit def monad: Monad[F]

  def put(blockHash: BlockHash, blockMessage: BlockMessage): F[Unit]

  def get(blockHash: BlockHash): F[Option[BlockMessage]]

  def put(f: => (BlockHash, BlockMessage)): F[Unit]

  //FIXME carbon copy of map behavior
  def apply(blockHash: BlockHash)(implicit monadF: Monad[F]): F[BlockMessage] =
    get(blockHash).map(_.get)

  def contains(blockHash: BlockHash)(implicit monadF: Monad[F]): F[Boolean] =
    get(blockHash).map(_.isDefined)

  def asMap(): F[Map[BlockHash, BlockMessage]]
}

object BlockStore {

  def apply[F[_]](implicit ev: BlockStore[F]): BlockStore[F] = ev

  type BlockHash = ByteString
  sealed trait BlockStoreError extends Throwable
  // some errors that extend BlockStoreError

  type BlockStoreMonadError[M[_]] = MonadError[M, BlockStoreError]

  def createMapBased[F[_]](implicit
                           monadF: Monad[F],
                           refF: Ref[F, Map[BlockHash, BlockMessage]],
                           metricsF: Metrics[F]): BlockStore[F] = InMemBlockStore.create
}
