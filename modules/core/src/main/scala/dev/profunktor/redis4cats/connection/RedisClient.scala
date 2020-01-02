/*
 * Copyright 2018-2019 ProfunKtor
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

import cats.effect._
import cats.syntax.apply._
import cats.syntax.functor._
import dev.profunktor.redis4cats.effect.{ JRFuture, Log }
import io.lettuce.core.{ RedisClient => JRedisClient, RedisURI => JRedisURI }

sealed abstract case class RedisClient private (underlying: JRedisClient, uri: RedisURI)

object RedisClient {

  private[redis4cats] def acquireAndRelease[F[_]: Concurrent: ContextShift: Log](
      uri: => RedisURI
  ): (F[RedisClient], RedisClient => F[Unit]) = {
    val acquire: F[RedisClient] = Sync[F].delay {
      val jClient: JRedisClient = JRedisClient.create(uri.underlying)
      new RedisClient(jClient, uri) {}
    }

    val release: RedisClient => F[Unit] = client =>
      F.info(s"Releasing Redis connection: $uri") *>
        JRFuture.fromCompletableFuture(F.delay(client.underlying.shutdownAsync())).void

    (acquire, release)
  }

  private[redis4cats] def acquireAndReleaseWithoutUri[F[_]: Concurrent: ContextShift: Log]
    : F[(F[RedisClient], RedisClient => F[Unit])] =
    F.delay(RedisURI.fromUnderlying(new JRedisURI())).map(acquireAndRelease(_))

  def apply[F[_]: Concurrent: ContextShift: Log](uri: => RedisURI): Resource[F, RedisClient] = {
    val (acquire, release) = acquireAndRelease(uri)
    Resource.make(acquire)(release)
  }

  def fromUnderlyingWithUri(underlying: JRedisClient, uri: RedisURI): RedisClient =
    new RedisClient(underlying, uri) {}

}
