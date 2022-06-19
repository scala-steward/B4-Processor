package b4processor.modules.memory

import b4processor.Parameters
import chisel3._
import chisel3.util._

class DataMemoryBufferEntry(implicit params: Parameters) extends Bundle {
  val address = SInt(64.W)
  val tag = UInt(params.tagWidth.W)
  val data = UInt(64.W)
  val opcode = Bool()
  val function3 = UInt(3.W)
}
