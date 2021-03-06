package io.getquill.source.mirror

import scala.reflect.macros.whitebox.Context
import language.experimental.macros
import scala.util.Failure
import scala.util.Success
import io.getquill._
import io.getquill.ast.{ Ast, Ident }
import io.getquill.quotation.Quoted
import io.getquill.source.Source
import io.getquill.source.SourceMacro
import io.getquill.util.Messages.RichContext
import io.getquill.norm.Normalize
import io.getquill.source.BindVariables
import io.getquill.quotation.IsDynamic

object mirrorSource extends MirrorSourceTemplate

abstract class MirrorSourceTemplate extends Source[Row, Row] {

  def run[T](quoted: Quoted[Query[T]]): QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, T](quoted: Quoted[P1 => Query[T]]): P1 => QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, P2, T](quoted: Quoted[(P1, P2) => Query[T]]): (P1, P2) => QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, P2, P3, T](quoted: Quoted[(P1, P2, P3) => Query[T]]): (P1, P2, P3) => QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]

  def run[T](quoted: Quoted[Action[T]]): ActionMirror = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, T](quoted: Quoted[P1 => Action[T]]): List[P1] => BatchActionMirror = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, P2, T](quoted: Quoted[(P1, P2) => Action[T]]): List[(P1, P2)] => BatchActionMirror = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, P2, P3, T](quoted: Quoted[(P1, P2, P3) => Action[T]]): List[(P1, P2, P3)] => BatchActionMirror = macro MirrorSourceMacro.run[Row, Row]

  def run[T](quoted: Quoted[T]): QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, T](quoted: Quoted[P1 => T]): P1 => QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, P2, T](quoted: Quoted[(P1, P2) => T]): (P1, P2) => QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]
  def run[P1, P2, P3, T](quoted: Quoted[(P1, P2, P3) => T]): (P1, P2, P3) => QueryMirror[T] = macro MirrorSourceMacro.run[Row, Row]

  def mirrorConfig = config

  def probe(ast: Ast) =
    if (ast.toString.contains("Fail"))
      Failure(new IllegalStateException("The ast contains 'Fail'"))
    else
      Success(())

  case class ActionMirror(ast: Ast)

  def execute(ast: Ast) =
    ActionMirror(ast)

  case class BatchActionMirror(ast: Ast, bindList: List[Row])

  def execute(ast: Ast, bindList: List[Row => Row]) =
    BatchActionMirror(ast, bindList.map(_(Row())))

  case class QueryMirror[T](ast: Ast, binds: Row, extractor: Row => T)

  def query[T](ast: Ast, bind: Row => Row, extractor: Row => T) =
    QueryMirror(ast, bind(Row()), extractor)

  implicit def optionDecoder[T](implicit d: Decoder[T]) = new Decoder[Option[T]] {
    def apply(index: Int, row: Row) =
      row[Option[T]](index)
  }

  implicit def optionEncoder[T](implicit d: Encoder[T]) = new Encoder[Option[T]] {
    def apply(index: Int, value: Option[T], row: Row) =
      row.add(value)
  }

  implicit val longDecoder = new Decoder[Long] {
    def apply(index: Int, row: Row) =
      row[Long](index)
  }

  implicit val longEncoder = new Encoder[Long] {
    def apply(index: Int, value: Long, row: Row) =
      row.add(value)
  }

  implicit val intDecoder = new Decoder[Int] {
    def apply(index: Int, row: Row) =
      row[Int](index)
  }

  implicit val intEncoder = new Encoder[Int] {
    def apply(index: Int, value: Int, row: Row) =
      row.add(value)
  }

  implicit val stringDecoder = new Decoder[String] {
    def apply(index: Int, row: Row) =
      row[String](index)
  }

  implicit val stringEncoder = new Encoder[String] {
    def apply(index: Int, value: String, row: Row) =
      row.add(value)
  }
}

class MirrorSourceMacro(val c: Context) extends SourceMacro {
  import c.universe.{ Ident => _, _ }

  override protected def prepare(ast: Ast, params: List[Ident]) =
    IsDynamic(ast) match {
      case false =>
        val (normalized, bindings) = BindVariables(Normalize(ast), params)
        resolveSource[MirrorSourceTemplate].map(_.probe(normalized)) match {
          case Some(Failure(e)) => c.warn(s"Probe failed. Reason $e")
          case other            =>
        }
        c.info(normalized.toString)
        q"($normalized, $bindings)"
      case true =>
        q"""
          import io.getquill.norm._
          import io.getquill.ast._
          BindVariables(Normalize($ast: Ast), $params)
        """
    }
}
