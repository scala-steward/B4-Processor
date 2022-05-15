package b4processor.modules.fetch

import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3._

class Waiting extends Bundle {
  val isWaiting = Bool()
  val reason = new BranchType.Type
}

object Waiting {
  def notWaiting(): Waiting = (new Waiting).Lit(_.isWaiting -> false.B, _.reason -> BranchType.None)

  def waitFor(branchType: BranchType.Type): Waiting = (new Waiting).Lit(_.isWaiting -> true.B, _.reason -> branchType)
}