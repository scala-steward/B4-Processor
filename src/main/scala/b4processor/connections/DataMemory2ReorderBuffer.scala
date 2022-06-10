package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

class DataMemory2ReorderBuffer(implicit params: Parameters) extends ReadyValidIO(new Bundle {
  val tag = UInt(params.tagWidth.W)
  val data = UInt(64.W)
})
