package trembita

import cats._
import trembita.internal._
import trembita.operations.LiftPipeline
import scala.annotation.unchecked.uncheckedVariance
import scala.language.{higherKinds, implicitConversions}
import scala.reflect.ClassTag

/**
  * Generic class representing a lazy pipeline of data
  *
  * @tparam A - type of data
  **/
trait BiDataPipelineT[F[_], +Er, +A, E <: Environment] extends Serializable with WithErrorCatchedSyntax {

  /**
    * Functor.map
    *
    * @tparam B - resulting data type
    * @param f - transformation function
    * @return - transformed [[BiDataPipelineT]]
    **/
  protected[trembita] def mapImpl[B: ClassTag](f: A => B)(
      implicit F: Monad[F]
  ): BiDataPipelineT[F, Er, B, E]

  /**
    *
    * @tparam B - resulting data type
    * @param f - transformation function from [[A]] into {{{Iterable[B]}}}
    * @return - transformed [[BiDataPipelineT]]
    **/
  protected[trembita] def mapConcatImpl[B: ClassTag](
      f: A => Iterable[B]
  )(implicit F: Monad[F]): BiDataPipelineT[F, Er, B, E]

  /**
    * Guarantees that [[BiDataPipelineT]]
    * will consists of elements satisfying given predicate
    *
    * @param p - predicate
    * @return - filtered [[BiDataPipelineT]]
    **/
  protected[trembita] def filterImpl[AA >: A](p: A => Boolean)(implicit F: Monad[F], A: ClassTag[AA]): BiDataPipelineT[F, Er, AA, E] =
    collectImpl[AA]({ case a if p(a) => a })

  /**
    * Applies a [[PartialFunction]] to the [[BiDataPipelineT]]
    *
    * @tparam B - resulting data type
    * @param pf - partial function
    * @return - transformed [[BiDataPipelineT]]
    **/
  protected[trembita] def collectImpl[B: ClassTag](pf: PartialFunction[A, B])(
      implicit F: Monad[F]
  ): BiDataPipelineT[F, Er, B, E]

  protected[trembita] def mapMImpl[AA >: A, B: ClassTag](f: A => F[B])(implicit F: Monad[F]): BiDataPipelineT[F, Er, B, E] =
    new MapMonadicPipelineT[F, Er, A, B, E](f, this)(F)

  protected[trembita] def handleErrorImpl[Err >: Er, B >: A: ClassTag](f: Err => B)(
      implicit F: MonadError[F, Err]
  ): BiDataPipelineT[F, Err, B, E]

  protected[trembita] def handleErrorWithImpl[Err >: Er, B >: A: ClassTag](f: Err => F[B])(
      implicit F: MonadError[F, Err]
  ): BiDataPipelineT[F, Err, B, E]

  /**
    * Forces evaluation of [[BiDataPipelineT]]
    * collecting data into [[Iterable]]
    *
    * @return - collected data
    **/
  protected[trembita] def evalFunc[B >: A](Ex: E @uncheckedVariance)(
      implicit run: Ex.Run[F]
  ): F[Ex.Repr[B]]
}

class WithErrorCatched[a, b](val `this`: a => b) extends AnyVal {
  def withErrorCatched[F[_], Er](implicit F: MonadError[F, Er]): a => F[b] = a => F.ap(F.pure(`this`))(F.pure(a))
}

trait WithErrorCatchedSyntax {
  implicit def convert[a, b](f: a => b): WithErrorCatched[a, b] = new WithErrorCatched(f)
}

object BiDataPipelineT {

  /** Implicit conversions */
  implicit def fromIterable[Er, A: ClassTag, F[_], Ex <: Environment](
      iterable: Iterable[A]
  )(implicit liftPipeline: LiftPipeline[F, Er, Ex]): BiDataPipelineT[F,Er, A, Ex] =
    liftPipeline.liftIterable(iterable)

  implicit def fromArray[Er, A: ClassTag, F[_], Ex <: Environment](
      array: Array[A]
  )(implicit liftPipeline: LiftPipeline[F, Er, Ex]): BiDataPipelineT[F, Er,A, Ex] =
    liftPipeline.liftIterable(array.toIterable)
}