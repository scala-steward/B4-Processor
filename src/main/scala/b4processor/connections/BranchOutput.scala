package b4processor.connections

import chisel3._
import chisel3.experimental.BundleLiterals._

class BranchOutput extends Bundle {
  val valid = Bool()
  val programCounter = SInt(64.W)
}

object BranchOutput {
  def noResult(): BranchOutput = (new BranchOutput).Lit(_.valid -> false.B, _.programCounter -> 0.S)

  def branch(address: SInt): BranchOutput = (new BranchOutput).Lit(_.valid -> true.B, _.programCounter -> address)
}