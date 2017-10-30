package jto.validation
package v3.tagless
package xml

import jto.validation.xml.Rules
import shapeless.tag, tag.@@
import scala.xml.NodeSeq
// import cats.syntax.cartesian._

trait RulesGrammar
  extends XmlGrammar[NodeSeq, Rule]
  with RuleConstraints
  with RulesTypeclasses[NodeSeq] {
  self =>

  type N = NodeSeq
  type Out = N
  type Sub = N
  type P = RulesGrammar

  def mapPath(f: Path => Path): P =
    new RulesGrammar {
      override def at(p: Path) =
        self.at(f(p))
    }

  /**
  * Find the node with a given name
  */
  @inline private def lookup(key: String, nodes: N): N =
    for {
      node <- nodes if node.label == key
    } yield node


  @inline private def search(path: Path, n: N): Option[N] =
    path.path match {
      case KeyPathNode(key) :: Nil =>
        val ns = lookup(key, n)
        ns.headOption.map { _ => ns }

      case KeyPathNode(key) :: tail =>
        val ns = lookup(key, n).flatMap(_.child)
        ns.headOption.flatMap { _ =>
          search(Path(tail), ns)
        }

      case IdxPathNode(idx) :: tail =>
        // TODO: check this one
        ???
        // (node \ "_")
        //   .lift(idx)
        //   .flatMap(childNode => search(Path(tail), childNode))

      case Nil =>
        Some(n)
    }

  def at(p: Path): At[Rule, Out, NodeSeq] =
    new At[Rule, Out, NodeSeq] {
      def run: Rule[Out, Option[NodeSeq]] =
        Rule.zero[Out].repath(_ => p).map{ search(p, _) }
    }

  def attr(key: String): At[Rule, Out, N] =
    new At[Rule, Out, N] {
      def run: Rule[Out, Option[N]] =
        Rule { out =>
          val path = Path(s"@$key")
          val ns = out.flatMap(_.attributes.filter(_.key == key).flatMap(_.value))
          Valid(path -> ns.headOption.map { _ => ns })
        }

    }

  def is[A](implicit K: Rule[_ >: Out <: N, A]): Rule[N, A] = K

  def opt[A](implicit K: Rule[_ >: Out <: N, A]): Rule[Option[N], Option[A]] =
    Rule {
      case Some(x) =>
        K.validateWithPath(x).map{ case (p, o) => (p, Option(o)) }
      case None =>
        Valid(Path -> None)
    }

  def req[A](implicit K: Rule[_ >: Out <: N, A]): Rule[Option[N], A] =
    Rule {
      case Some(x) =>
        K.validateWithPath(x)
      case None =>
        Invalid(Seq(Path -> Seq(ValidationError("error.required"))))
    }

  private def nodeR[O](implicit r: RuleLike[String, O]): Rule[N, O] @@ Root =
    tag[Root] {
      val err =
        Invalid(Seq(ValidationError(
                        "error.invalid",
                        "a non-leaf node can not be validated to a primitive type")))
      Rule.fromMapping[NodeSeq, String] { case ns =>
          val children = (ns \ "_")
          if (children.isEmpty) Valid(ns.head.text)
          else err
        }
      .andThen(r)
    }

  implicit def int = nodeR(Rules.intR)
  implicit def string = nodeR(Rule.zero)
  implicit def bigDecimal = nodeR(Rules.bigDecimal)
  implicit def boolean = nodeR(Rules.booleanR)
  implicit def double = nodeR(Rules.doubleR)
  implicit def float = nodeR(Rules.floatR)
  implicit def jBigDecimal = nodeR(Rules.javaBigDecimalR)
  implicit def long = nodeR(Rules.longR)
  implicit def short = nodeR(Rules.shortR)

  implicit def list[A](implicit k: Rule[_ >: Out <: N, A]) =
    Rule[NodeSeq, List[A]] { ns =>
      import cats.instances.list._
      import cats.syntax.traverse._
      ns.theSeq.toList.zipWithIndex.map { case (n, i) =>
        k.repath(_ \ i).validate(n)
      }.sequenceU.map(Path -> _)
    }

  implicit def map[A](implicit k: Rule[_ >: Out <: N, A]) =
    ???

  def toGoal[Repr, A] = _.map { Goal.apply }
}

object RulesGrammar extends RulesGrammar