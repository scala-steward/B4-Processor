package b4processor.utils

import b4processor.utils.EitherBundle.{fromLeft, fromRight}
import chisel3._
import chisel3.util._

class EitherBundle[L <: Data, R <: Data](l: L, r: R) extends Bundle {
  val common_width = l.getWidth max r.getWidth
  val common_data = UInt(common_width.W)
  val is_left = Bool()

  def matchExhaustive[T <: Data](left: L => T, right: R => T): T = {
    val left_val = left(this.common_data(l.getWidth - 1, 0).asTypeOf(l))
    val right_val = right(this.common_data(r.getWidth - 1, 0).asTypeOf(r))
    Mux(this.is_left, left_val, right_val)
  }

  def setLeft(v: L) = {
    this.is_left := true.B
    this.common_data := v.asTypeOf(this.common_data)
  }

  def setRight(v: R) = {
    this.is_left := false.B
    this.common_data := v.asTypeOf(this.common_data)
  }

  def mapLeft[LL <: Data](m: L => LL): EitherBundle[LL, R] =
    this.matchExhaustive({ l => fromLeft(m(l)) }, { r => fromRight(r) })

}

object EitherBundle {
  def fromLeft[L <: Data, R <: Data](v: L): EitherBundle[L, R] = {
    val w = Wire(new EitherBundle(v, (new R).cloneType))
    w.common_data := v.asTypeOf(w.common_data)
    w.is_left := true.B
    w
  }

  def fromRight[L <: Data, R <: Data](v: R): EitherBundle[L, R] = {
    val w = Wire(new EitherBundle((new L).cloneType, v))
    w.common_data := v.asTypeOf(w.common_data)
    w.is_left := false.B
    w
  }
}
