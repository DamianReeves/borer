/* Magnolia, version 0.10.0. Copyright 2018 Jon Pretty, Propensive Ltd.
 *
 * The primary distribution site is: http://co.ntextu.al/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.bullet.borer.magnolia.examples

import io.bullet.borer.magnolia._

/** shows one type as another, often as a string
  *
  *  Note that this is a more general form of `Show` than is usual, as it permits the return type to
  *  be something other than a string. */
trait Show[Out, T] { def show(value: T): Out }

trait GenericShow[Out] {

  /** the type constructor for new [[Show]] instances
    *
    *  The first parameter is fixed as `String`, and the second parameter varies generically. */
  type Typeclass[T] = Show[Out, T]

  def join(typeName: String, strings: Seq[String]): Out
  def prefix(s: String, out: Out): Out

  /** creates a new [[Show]] instance by labelling and joining (with `mkString`) the result of
    *  showing each parameter, and prefixing it with the class name */
  def combine[T](ctx: CaseClass[Typeclass, T]): Show[Out, T] = { value =>
    if (ctx.isValueClass) {
      val param = ctx.parameters.head
      param.typeclass.show(param.dereference(value))
    } else {
      val paramStrings = ctx.parameters.map { param =>
        val attribStr =
          if (param.annotations.isEmpty) ""
          else {
            param.annotations.mkString("{", ", ", "}")
          }
        s"${param.label}$attribStr=${param.typeclass.show(param.dereference(value))}"
      }

      val anns          = ctx.annotations.filterNot(_.isInstanceOf[scala.SerialVersionUID])
      val annotationStr = if (anns.isEmpty) "" else anns.mkString("{", ",", "}")

      def typeArgsString(typeName: TypeName): String =
        if (typeName.typeArguments.isEmpty) ""
        else typeName.typeArguments.map(arg => s"${arg.short}${typeArgsString(arg)}").mkString("[", ",", "]")

      join(ctx.typeName.short + typeArgsString(ctx.typeName) + annotationStr, paramStrings)
    }
  }

  /** choose which typeclass to use based on the subtype of the sealed trait
    * and prefix with the annotations as discovered on the subtype. */
  def dispatch[T](ctx: SealedTrait[Typeclass, T]): Show[Out, T] =
    (value: T) =>
      ctx.dispatch(value) { sub =>
        val anns          = sub.annotations.filterNot(_.isInstanceOf[scala.SerialVersionUID])
        val annotationStr = if (anns.isEmpty) "" else anns.mkString("{", ",", "}")
        prefix(annotationStr, sub.typeclass.show(sub.cast(value)))
      }

  /** bind the Magnolia macro to this derivation object */
  implicit def gen[T]: Show[Out, T] = macro Magnolia.gen[T]
}

/** companion object to [[Show]] */
object Show extends GenericShow[String] {

  /** show typeclass for strings */
  implicit val string: Show[String, String] = (s: String) => s

  def join(typeName: String, params: Seq[String]): String =
    params.mkString(s"$typeName(", ",", ")")
  def prefix(s: String, out: String): String = s + out

  /** show typeclass for integers */
  implicit val int: Show[String, Int] = (s: Int) => s.toString

  /** show typeclass for sequences */
  implicit def seq[A](implicit A: Show[String, A]): Show[String, Seq[A]] =
    new Show[String, Seq[A]] {
      def show(as: Seq[A]): String = as.iterator.map(A.show).mkString("[", ",", "]")
    }
}
