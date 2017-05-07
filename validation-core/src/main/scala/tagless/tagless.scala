package jto.validation
package v3.tagless

import shapeless.{ ::, HNil, HList }
import shapeless.tag.@@

object types {
  type flip[F[_, _]] = { type λ[B, A] = F[A, B] }
}

case class Goal[A, B](value: A) {
  def trivial(implicit ev: A =:= (B :: HNil)): B = value.head
}

trait Merge[F[_]] {
  def merge[A, B <: HList](fa: F[A], fb: F[B]): F[A :: B]
}

case class MergeOps[F[_], B <: HList](fb: F[B])(implicit M: Merge[F]) {
  def ~:[A](fa: F[A]): F[A :: B] = M.merge(fa, fb)
}

trait Primitives[I, K[_, _]] {
  self: Constraints[K] =>

  type J <: I

  def at[A](p: Path)(k:  => K[_ >: J <: I, A]): K[J, A]
  def opt[A](p: Path)(k: => K[_ >: J <: I, A]): K[J, Option[A]]
  def knil: K[J, HNil]

  def is[A](implicit K: K[_ >: J <: I, A]): K[I, A] = K.asInstanceOf[K[I, A]]

  def toGoal[Repr, A]: K[J, Repr] => K[J, Goal[Repr, A]]

  sealed trait Defered[A] {
    def apply[Repr](k: K[J, Repr]): K[J, Goal[Repr, A]] = toGoal(k)
  }

  def goal[A] = new Defered[A]{}

  implicit def int: K[I, Int] @@ Root
  implicit def string: K[I, String] @@ Root
  implicit def short: K[I, Short] @@ Root
  implicit def long: K[I, Long] @@ Root
  implicit def float: K[I, Float] @@ Root
  implicit def double: K[I, Double] @@ Root
  implicit def jBigDecimal: K[I, java.math.BigDecimal] @@ Root
  implicit def bigDecimal: K[I, BigDecimal] @@ Root
  implicit def boolean: K[I, Boolean] @@ Root
  implicit def seq[A](implicit k: K[_ >: J <: I, A]): K[I, Seq[A]]
  implicit def list[A](implicit k: K[_ >: J <: I, A]): K[I, List[A]]
  implicit def array[A: scala.reflect.ClassTag](implicit k: K[_ >: J <: I, A]): K[I, Array[A]]
  implicit def map[A](implicit k: K[_ >: J <: I, A]): K[I, Map[String, A]]
  implicit def traversable[A](implicit k: K[_ >: J <: I, A]): K[I, Traversable[A]]

  import cats.arrow.Compose
  implicit def composeTC: Compose[K]
  implicit def semigroupTC[I0, O]: cats.Semigroup[K[I0, O] @@ Root]
  implicit def mergeTC: Merge[K[J, ?]]

  implicit def toMergeOps[B <: HList](fb: K[J, B]): MergeOps[K[J, ?], B] =
    MergeOps[K[J, ?], B](fb)(mergeTC)
}

trait Constraints[K[_, _]] {
  type C[A] = K[A, A] @@ Root

  def min[A](a: A)(implicit O: Ordering[A]): C[A]
  def max[A](a: A)(implicit O: Ordering[A]): C[A]
  def notEmpty: C[String]
  def minLength(l: Int): C[String]
  def email: C[String]
  def forall[I, O](k: K[I, O]): K[Seq[I], Seq[O]]
  def equalTo[A](a: A): C[A]
}

trait Grammar[I, K[_, _]]
  extends Primitives[I, K]
  with Constraints[K]
