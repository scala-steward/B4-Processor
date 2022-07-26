package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

class Fetch2BranchBuffer(implicit params: Parameters) extends Bundle {
  val branches = Vec(
    params.runParallel,
    new Bundle {
      val valid = Output(Bool())
      val ready = Input(Bool())
      val address = Output(SInt(64.W))
      val branchID = Input(UInt(params.branchBufferSize.W))
    }
  )
  val changeAddress = Flipped(Valid(SInt(64.W)))
}
