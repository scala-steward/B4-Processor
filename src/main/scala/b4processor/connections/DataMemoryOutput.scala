package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

class DataMemoryOutput(implicit params: Parameters) extends Valid(new Bundle {
  val tag = UInt(params.tagWidth.W)
  val value = UInt(64.W)
})
