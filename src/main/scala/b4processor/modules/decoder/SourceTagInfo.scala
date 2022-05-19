package b4processor.modules.decoder

import b4processor.Parameters
import chisel3._

class SourceTagInfo(implicit params: Parameters) extends Bundle {
  val valid = Bool()
  val tag = UInt(params.tagWidth.W)
  val from = new SourceTagFrom.Type
}
