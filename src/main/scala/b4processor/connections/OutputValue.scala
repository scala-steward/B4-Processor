package b4processor.connections

import b4processor.Parameters
import chisel3._

class OutputValue(implicit params: Parameters) extends Bundle {
  val validAsResult = Output(Bool())
  val validAsLoadStoreAddress = Output(Bool())
  val value = Output(UInt(64.W))
  val tag = Output(UInt(params.tagWidth.W))
}
