package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._

class SourceTagInfo(implicit params: Parameters) extends Bundle {
  val valid = Bool()
  val tag = new Tag()
  val from = new SourceTagFrom.Type
}
