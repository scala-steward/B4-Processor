package b4processor.modules.memory

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._

class WriteResponse(implicit params: Parameters) extends Bundle {
  val tag = new Tag
  val isError = Bool()
}
