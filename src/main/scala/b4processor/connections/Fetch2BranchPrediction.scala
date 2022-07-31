package b4processor.connections

import b4processor.Parameters
import chisel3._

class Fetch2BranchPrediction(implicit params: Parameters) extends Bundle {
  val address = Output(SInt(64.W))
  val branchID = Output(UInt(params.branchBufferSize.W))
  val prediction = Input(Bool())
}
