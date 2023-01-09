package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

class ReorderBuffer2CSR(implicit params: Parameters) extends Bundle {
  val retireCount = Valid(UInt(log2Up(params.maxRegisterFileCommitCount).W))
  val mcause = Valid(UInt(64.W))
}
