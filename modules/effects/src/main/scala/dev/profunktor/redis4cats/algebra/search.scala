package dev.profunktor.redis4cats.algebra

import com.redis.lettucemod.search.{AggregateOptions, AggregateResults, AggregateWithCursorResults, CreateOptions, Cursor, Field, SearchOptions, SearchResults, SuggetOptions, Suggestion => LettuceSuggestion}

trait SearchCommands[F[_], K, V]
  extends Create[F, K, V]
  with Search[F, K, V]
  with Update[F, K]
  with Delete[F, K, V]
  with Alias[F, K, V]
  with Tags[F, K, V]
  with Suggestion[F, K, V]
  with Dictionary[F, K, V]
  with Info[F, K]

trait Create[F[_], K, V] {
  def create(index: K, options: Option[CreateOptions[K, V]], fields: Field*): F[Unit]
}

trait Search[F[_], K, V] {
  def search(index: K, query: V, options: Option[SearchOptions[K, V]] = None): F[SearchResults[K, V]]

  def aggregate(index: K, query: V, options: Option[AggregateOptions[K, V]] = None): F[AggregateResults[K]]

  def aggregate(index: K, query: V, cursor: Cursor, options: Option[AggregateOptions[K, V]]): F[AggregateWithCursorResults[K]]

  def cursorRead(index: K, cursor: Long): F[AggregateWithCursorResults[K]]

  def cursorRead(index: K, cursor: Long, count: Long): F[AggregateWithCursorResults[K]]

  def cursorDelete(index: K, cursor: Long): F[Boolean] //TODO if delete true?
}

trait Update[F[_], K] {
  def alter(index: K, field: Field): F[Unit] // TODO what if field already exist?
}

trait Delete[F[_], K, V] {
  def dropIndex(index: K): F[Unit] // TODO boolean?

  def dropIndexDeleteDocs(index: K): F[Unit] // TODO boolean?
}

trait Alias[F[_], K, V] {
  def aliasAdd(name: K, index: V): F[Boolean]

  def aliasUpdate(name: K, index: V): F[Boolean]

  def aliasDel(name: K): F[Boolean]
}

trait Tags[F[_], K, V] {
  def tagVals(index: K, field: K): F[Seq[V]]
}

trait Suggestion[F[_], K, V] {
  def sugAddSet(key: String, string: V, value: Double): F[Unit]

  def sugAddIncr(key: String, string: V, value: Double): F[Unit]

  def sugAddSet(key: String, string: V, value: Double, payload: V): F[Unit]

  def sugAddIncr(key: String, string: V, value: Double, payload: V): F[Unit]

  def sugGet(key: K, prefix: String, options: Option[SuggetOptions]): F[Seq[LettuceSuggestion[V]]]

  def sugDel(key: K, string: V): F[Boolean]

  def sugLen(key: K): F[Long]
}

trait Dictionary[F[_], K, V] {
  def dictAdd(dict: K, terms: V*): F[Long]

  def dictDel(dict: K, terms: V*): F[Long]

  def dictDump(dict: K): F[Seq[V]]
}

trait Info[F[_], K] {
  def indexInfo(index: K): F[List[Any]] //TODO looks not very typesafe :(

  def list: F[Seq[K]]
}
