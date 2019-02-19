package trembita.internal

import cats._
import trembita._
import trembita.operations.{CanGroupBy, CanGroupByOrdered}
import scala.annotation.unchecked.uncheckedVariance
import scala.language.higherKinds
import scala.reflect.ClassTag

/**
  * Special case for [[BiDataPipelineT]]
  * representing a pipeline of tuples
  * with UNIQUE keys
  *
  * @tparam K - key
  * @tparam V - value
  **/
trait BiMapPipelineT[F[_], Er, K, V, E <: Environment]
    extends BiDataPipelineT[F, Er, (K, V), E] {

  /**
    * Applies mapping function only for the values
    *
    * @tparam W - resulting value type
    * @param f - transformation function
    * @return - a pipeline with the same key and transformed values
    **/
  def mapValues[W: ClassTag](f: V => W)(implicit F: Monad[F]): BiMapPipelineT[F,Er, K, W, E]

  /**
    * Returns only those ([[K]], [[V]]) pairs
    * that satisfies given predicate
    *
    * @param p - predicate
    * @return - filtered [[BiMapPipelineT]]
    **/
  def filterKeys(p: K => Boolean)(
    implicit F: Monad[F]
  ): BiMapPipelineT[F,Er, K, V, E]

  /** @return - pipeline with keys only */
  def keys(implicit F: Monad[F]): BiDataPipelineT[F,Er, K, E]

  /** @return - pipeline with values only */
  def values(implicit F: Monad[F]): BiDataPipelineT[F,Er, V, E]
}

/**
  * Sequential implementation of [[BiMapPipelineT]]
  *
  * @tparam K -key
  * @tparam V - value
  * @param source - (K, V) pair pipeline
  **/
protected[trembita] class BaseMapPipelineT[F[_], Er, K, V, E <: Environment](
  source: BiDataPipelineT[F,Er, (K, V), E],
  F: Monad[F]
)(implicit K: ClassTag[K], V: ClassTag[V]) extends SeqSource[F,Er, (K, V), E](F)
    with BiMapPipelineT[F,Er, K, V, E] {

  def mapValues[W: ClassTag](f: V => W)(implicit F: Monad[F]): BiMapPipelineT[F,Er, K, W, E@uncheckedVariance] =
    new BaseMapPipelineT[F,Er, K, W, E](source.mapImpl{case (k, v) => k -> f(v)}, F)

  def filterKeys(
    p: K => Boolean
  )(implicit F: Monad[F]): BiMapPipelineT[F,Er, K, V, E] =
    new BaseMapPipelineT[F,Er, K, V, E](source.collectImpl {
      case (k, v) if p(k) => (k, v)
    }, F)

  def keys(implicit F: Monad[F]): BiDataPipelineT[F,Er, K, E] =
    new MappingPipelineT[F,Er, (K, V), K, E](_._1, this)(F)

  def values(implicit F: Monad[F]): BiDataPipelineT[F,Er, V, E] =
    new MappingPipelineT[F,Er, (K, V), V, E](_._2, this)(F)

  protected[trembita] def evalFunc[B >: (K, V)](E: E)(implicit run: E.Run[F]): F[E.Repr[B]] =
    F.map(
      source
        .evalFunc[(K, V)](E)
    )(vs => E.distinctKeys(vs).asInstanceOf[E.Repr[B]])

  override def handleErrorImpl[Err >: Er, B >: (K, V): ClassTag](
    f: Err => B
  )(implicit F: MonadError[F, Err]): BiDataPipelineT[F,Err, B, E] =
    new BaseMapPipelineT[F,Err, K, V, E](
      source
        .handleErrorImpl[Err, B](f).asInstanceOf[BiDataPipelineT[F, Err, (K, V), E]],
      F
    )
}

/**
  * A [[BiDataPipelineT]]
  * been grouped by some criteria
  *
  * @tparam K - grouping criteria type
  * @tparam V - value
  * @param f - grouping function
  **/
object GroupByPipelineT {
  def make[F[_],Er, K, V, Ex <: Environment](
                                           f: V => K,
                                           source: BiDataPipelineT[F,Er, V, Ex],
                                           F: Monad[F],
                                           canGroupBy: CanGroupBy[Ex#Repr]
                                         )(
    implicit K: ClassTag[K], V: ClassTag[V]
  ): BiDataPipelineT[F,Er, (K, Iterable[V]), Ex]   =
 new SeqSource[F,Er, (K, Iterable[V]), Ex](F) {
  protected[trembita] def evalFunc[B >: (K, Iterable[V])](Ex: Ex)(implicit run: Ex.Run[F]): F[Ex.Repr[B]] =
    F.map(
      source
        .evalFunc[V](Ex)
    )(
      vs =>
        canGroupBy.groupBy(vs.asInstanceOf[Ex#Repr[V]])(f).asInstanceOf[Ex.Repr[B]]
    )
  }
}

/**
  * A [[BiDataPipelineT]]
  * been grouped by some criteria
  *
  * @tparam K - grouping criteria type
  * @tparam V - value
  * @param f - grouping function
  **/
object GroupByOrderedPipelineT {
  def make[F[_],Er, K, V, Ex <: Environment](
                                           f: V => K,
                                           source: BiDataPipelineT[F,Er, V, Ex],
                                           F: Monad[F],
                                           canGroupBy: CanGroupByOrdered[Ex#Repr]
                                         )(
                                           implicit K: ClassTag[K], V: ClassTag[V],
                                           ordering: Ordering[K]
                                         ): BiDataPipelineT[F,Er, (K, Iterable[V]), Ex]   =
    new SeqSource[F, Er, (K, Iterable[V]), Ex](F) {
      protected[trembita] def evalFunc[B >: (K, Iterable[V])](Ex: Ex)(implicit run: Ex.Run[F]): F[Ex.Repr[B]] =
        F.map(
          source
            .evalFunc[V](Ex)
        )(
          vs =>
            canGroupBy.groupBy(vs.asInstanceOf[Ex#Repr[V]])(f).asInstanceOf[Ex.Repr[B]]
        )
    }
}