package b4processor.connections

import chisel3._
import chisel3.experimental.BundleLiterals._

class ExecutorBranchResult extends Bundle {
  val valid = Bool()
  val branchAddress = SInt(64.W)
}

object ExecutorBranchResult {
  def noResult(): ExecutorBranchResult = (new ExecutorBranchResult).Lit(_.valid -> false.B, _.branchAddress -> 0.S)

  def branch(address: SInt): ExecutorBranchResult = (new ExecutorBranchResult).Lit(_.valid -> true.B, _.branchAddress -> address)
}