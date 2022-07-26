package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.experimental.BundleLiterals._

class BranchOutput(implicit params: Parameters) extends Bundle {
  val valid = Bool()
  val address = SInt(64.W)
  val branchID = UInt(params.branchBufferSize.W)
}

object BranchOutput {
  def noResult(implicit params: Parameters): BranchOutput = {
    val w = Wire(new BranchOutput)
    w.valid := false.B
    w.address := DontCare
    w.branchID := DontCare
    w
  }

  def branch(address: SInt, branchID: UInt)(implicit
                                            params: Parameters
  ): BranchOutput = {
    val w = Wire(new BranchOutput)
    w.valid := true.B
    w.address := address
    w.branchID := branchID
    w
  }
}
