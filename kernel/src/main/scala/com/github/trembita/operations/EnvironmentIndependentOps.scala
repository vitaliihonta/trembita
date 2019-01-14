package com.github.trembita.operations
import cats.data.Kleisli
import cats.{~>, Monad, MonadError}
import com.github.trembita.internal._
import com.github.trembita.{operations, DataPipelineT, Environment}

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.reflect.ClassTag

trait EnvironmentIndependentOps[F[_], A, E <: Environment] extends Any {
  def `this`: DataPipelineT[F, A, E]

  def flatten[B: ClassTag](implicit ev: A <:< Iterable[B], F: Monad[F]): DataPipelineT[F, B, E] =
    `this`.mapConcatImpl(ev)

  /**
    * Forces evaluation of [[E]] internal representation so that further transformations won't be chained with previous ones.
    * Examples:
    * - for sequential pipeline it leads to intermediate collection allocation
    * - for Akka / Spark pipelines it's not such necessary
    * */
  def memoize()(implicit A: ClassTag[A], F: Monad[F]): DataPipelineT[F, A, E] =
    new MemoizedPipelineT[F, A, E](`this`, F)

  /**
    * Groups the pipeline using given grouping criteria.
    *
    * Returns a [[GroupByPipelineT]] - special implementation of [[DataPipelineT]]
    *
    * @return - a data pipeline consisting of pair {{{ (K, Iterable[A]) }}}
    **/
  def groupBy[K: ClassTag](f: A => K)(
      implicit canGroupBy: CanGroupBy[E#Repr],
      F: Monad[F],
      A: ClassTag[A]
  ): DataPipelineT[F, (K, Iterable[A]), E] = GroupByPipelineT.make[F, K, A, E](f, `this`, F, canGroupBy)

  /**
    * Groups the pipeline using given grouping criteria guaranteeing keys ordering.
    *
    * Returns a [[GroupByPipelineT]] - special implementation of [[DataPipelineT]]
    *
    * @return - a data pipeline consisting of pair {{{ (K, Iterable[A]) }}}
    **/
  def groupByOrdered[K: ClassTag: Ordering](f: A => K)(
      implicit canGroupByOrdered: CanGroupByOrdered[E#Repr],
      F: Monad[F],
      A: ClassTag[A]
  ): DataPipelineT[F, (K, Iterable[A]), E] = GroupByOrderedPipelineT.make[F, K, A, E](f, `this`, F, canGroupByOrdered)

  /**
    * Special case of [[distinctBy]]
    * Guarantees that each element of pipeline is unique
    *
    * CONTRACT: the caller is responsible for correct {{{equals}}}
    * implemented for type [[A]]
    *
    * @return - pipeline with only unique elements
    **/
  def distinct(implicit canGroupBy: CanGroupBy[E#Repr], A: ClassTag[A], F: Monad[F]): DataPipelineT[F, A, E] =
    distinctBy(identity)

  /**
    * Guarantees that each element of pipeline is unique
    * according to the given criteria
    *
    * CONTRACT: the caller is responsible for correct {{{equals}}}
    *
    * @return - pipeline with only unique elements
    **/
  def distinctBy[K: ClassTag](f: A => K)(implicit canGroupBy: CanGroupBy[E#Repr], F: Monad[F], A: ClassTag[A]): DataPipelineT[F, A, E] =
    groupBy(f).mapImpl(_._2.head)

  def zip[B: ClassTag](
      that: DataPipelineT[F, B, E]
  )(implicit A: ClassTag[A], F: Monad[F], canZip: CanZip[E#Repr]): DataPipelineT[F, (A, B), E] =
    new ZipPipelineT[F, A, B, E](`this`, that, canZip)

  def ++(that: DataPipelineT[F, A, E])(implicit A: ClassTag[A], F: Monad[F]): DataPipelineT[F, A, E] =
    new ConcatPipelineT[F, A, E](`this`, that)

  def join[B](that: DataPipelineT[F, B, E])(on: (A, B) => Boolean)(
      implicit canJoin: CanJoin[E#Repr],
      A: ClassTag[A],
      B: ClassTag[B],
      F: Monad[F]
  ): DataPipelineT[F, (A, B), E] =
    new JoinPipelineT[F, A, B, E](`this`, that, on)

  def joinLeft[B](that: DataPipelineT[F, B, E])(on: (A, B) => Boolean)(
      implicit canJoin: CanJoin[E#Repr],
      A: ClassTag[A],
      B: ClassTag[B],
      F: Monad[F]
  ): DataPipelineT[F, (A, Option[B]), E] =
    new JoinLeftPipelineT[F, A, B, E](`this`, that, on)

  def joinRight[B](that: DataPipelineT[F, B, E])(on: (A, B) => Boolean)(
      implicit canJoin: CanJoin[E#Repr],
      A: ClassTag[A],
      B: ClassTag[B],
      F: Monad[F]
  ): DataPipelineT[F, (Option[A], B), E] =
    new JoinRightPipelineT[F, A, B, E](`this`, that, on)

  /**
    * Allows to pause elements evaluation with given duration based on single [[A]]
    * */
  def pausedWith(getPause: A => FiniteDuration)(implicit ev: F CanPause E, A: ClassTag[A]): DataPipelineT[F, A, E] =
    ev.pausedWith(`this`)(getPause)

  /**
    * Allows to pause elements evaluation with given duration based on [[A]]
    * if condition is true
    * */
  def pausedWithIf(cond: Boolean)(getPause: A => FiniteDuration)(implicit ev: F CanPause E, A: ClassTag[A]): DataPipelineT[F, A, E] =
    if (cond) ev.pausedWith(`this`)(getPause)
    else `this`

  /**
    * Allows to pause elements evaluation with given duration based on 2 elements of type [[A]]
    * */
  def pausedWith2(getPause: (A, A) => FiniteDuration)(implicit ev: F CanPause2 E, A: ClassTag[A]): DataPipelineT[F, A, E] =
    ev.pausedWith(`this`)(getPause)

  /**
    * Allows to pause elements evaluation with given duration based on 2 elements of type [[A]]
    * if condition is true
    * */
  def pausedWith2If(cond: Boolean)(getPause: (A, A) => FiniteDuration)(implicit ev: F CanPause2 E, A: ClassTag[A]): DataPipelineT[F, A, E] =
    if (cond) ev.pausedWith(`this`)(getPause)
    else `this`

  /**
    * Allows to pause elements evaluation with given fixed duration
    * */
  def paused(pause: FiniteDuration)(implicit ev: F CanPause E, A: ClassTag[A]): DataPipelineT[F, A, E] =
    ev.paused(`this`)(pause)

  /**
    * Allows to pause elements evaluation with given fixed duration
    * if condition is true
    * */
  def pausedIf(cond: Boolean)(pause: FiniteDuration)(implicit ev: F CanPause E, A: ClassTag[A]): DataPipelineT[F, A, E] =
    if (cond) ev.paused(`this`)(pause)
    else `this`

  /**
    * Allows apply transformations defined as [[Kleisli]] on given pipeline
    * */
  def through[B](kleisli: Kleisli[DataPipelineT[F, ?, E], DataPipelineT[F, A, E], B]): DataPipelineT[F, B, E] =
    kleisli.run(`this`)

  /**
    * Like [[groupBy]] with the following difference:
    *
    * {{{
    *   // groupBy...
    *   List(1, 1, 2, 2, 1, 1).groupBy(identity) === Map(1 -> List(1, 1, 1, 1), 2 -> List(2, 2))
    *   // and spanBy...
    *   List(1, 1, 2, 2, 1, 1).spanBy(identity) === List(1 -> List(1, 1), 2 -> List(2, 2), 1 -> List(1, 1))
    * }}}
    *
    * */
  def spanBy[K](f: A => K)(
      implicit canSpanBy: CanSpanBy[E#Repr],
      A: ClassTag[A],
      K: ClassTag[K],
      F: Monad[F],
      e: E,
      run: E#Run[F]
  ): DataPipelineT[F, (K, Iterable[A]), E] =
    `this`.mapRepr(canSpanBy.spanBy(_)(f))
}