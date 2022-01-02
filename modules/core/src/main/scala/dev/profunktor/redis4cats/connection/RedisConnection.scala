/*
 * Copyright 2018-2021 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.redis4cats.connection

import cats.{ApplicativeThrow, MonadThrow}
import cats.effect.kernel.Async
import cats.syntax.all._
import dev.profunktor.redis4cats.data.NodeId
import dev.profunktor.redis4cats.effect.{FutureLift, RedisExecutor}
import scala.util.control.NoStackTrace
import com.redis.lettucemod.api.async.RedisModulesAsyncCommands
import com.redis.lettucemod.api.sync.{RedisModulesCommands => RedisModulesSyncCommands}
import com.redis.lettucemod.api.StatefulRedisModulesConnection
import com.redis.lettucemod.cluster.api.StatefulRedisModulesClusterConnection
import com.redis.lettucemod.cluster.api.async.RedisModulesClusterAsyncCommands
import com.redis.lettucemod.cluster.api.sync.{RedisModulesClusterCommands => RedisModulesClusterSyncCommands}

case class OperationNotSupported(value: String) extends NoStackTrace {
  override def toString: String = s"OperationNotSupported($value)"
}

private[redis4cats] trait RedisConnection[F[_], K, V] {
  def sync: F[RedisModulesSyncCommands[K, V]]
  def clusterSync: F[RedisModulesClusterSyncCommands[K, V]]
  def async: F[RedisModulesAsyncCommands[K, V]]
  def clusterAsync: F[RedisModulesClusterAsyncCommands[K, V]]
  def close: F[Unit]
  def byNode(nodeId: NodeId): F[RedisModulesAsyncCommands[K, V]]
  def liftK[G[_]: Async]: RedisConnection[G, K, V]
}

private[redis4cats] class RedisStatefulConnection[F[_]: ApplicativeThrow: FutureLift: RedisExecutor, K, V](
    conn: StatefulRedisModulesConnection[K, V]
) extends RedisConnection[F, K, V] {
  def sync: F[RedisModulesSyncCommands[K, V]] = RedisExecutor[F].delay(conn.sync())
  def clusterSync: F[RedisModulesClusterSyncCommands[K, V]] =
    OperationNotSupported("Running in a single node").raiseError
  def async: F[RedisModulesAsyncCommands[K, V]] = RedisExecutor[F].delay(conn.async())
  def clusterAsync: F[RedisModulesClusterAsyncCommands[K, V]] =
    OperationNotSupported("Running in a single node").raiseError
  def close: F[Unit] = FutureLift[F].liftCompletableFuture(RedisExecutor[F].delay(conn.closeAsync())).void
  def byNode(nodeId: NodeId): F[RedisModulesAsyncCommands[K, V]] =
    OperationNotSupported("Running in a single node").raiseError
  def liftK[G[_]: Async]: RedisConnection[G, K, V] = {
    implicit val ecG: RedisExecutor[G] = RedisExecutor[F].liftK[G]
    new RedisStatefulConnection[G, K, V](conn)
  }
}

private[redis4cats] class RedisStatefulClusterConnection[F[_]: FutureLift: MonadThrow: RedisExecutor, K, V](
    conn: StatefulRedisModulesClusterConnection[K, V]
) extends RedisConnection[F, K, V] {
  def sync: F[RedisModulesSyncCommands[K, V]] =
    OperationNotSupported("Transactions are not supported in a cluster. You must select a single node.").raiseError
  def async: F[RedisModulesAsyncCommands[K, V]] =
    OperationNotSupported("Transactions are not supported in a cluster. You must select a single node.").raiseError
  def clusterAsync: F[RedisModulesClusterAsyncCommands[K, V]] = RedisExecutor[F].delay(conn.async())
  def clusterSync: F[RedisModulesClusterSyncCommands[K, V]]   = RedisExecutor[F].delay(conn.sync())
  def close: F[Unit] =
    FutureLift[F].liftCompletableFuture(RedisExecutor[F].delay(conn.closeAsync())).void
  def byNode(nodeId: NodeId): F[RedisModulesAsyncCommands[K, V]] =
    FutureLift[F].liftCompletableFuture(RedisExecutor[F].delay(conn.getConnectionAsync(nodeId.value))).flatMap {
      stateful => RedisExecutor[F].delay(stateful.async())
    }
  def liftK[G[_]: Async]: RedisConnection[G, K, V] = {
    implicit val ecG: RedisExecutor[G] = RedisExecutor[F].liftK[G]
    new RedisStatefulClusterConnection[G, K, V](conn)
  }
}
