package b4processor.utils

import b4processor.Parameters
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.util._

class Tag(implicit params: Parameters) extends Bundle {
  val id = UInt(params.tagWidth.W)

  def ===(that: Tag): Bool = this.id === that.id
}

object Tag {
  def apply(id: Int)(implicit params: Parameters): Tag =
    new Tag().Lit(_.id -> id.U)

  def fromWires(id: UInt)(implicit params: Parameters): Tag = {
    val w = Wire(new Tag)
    w.id := id
    w
  }
}
