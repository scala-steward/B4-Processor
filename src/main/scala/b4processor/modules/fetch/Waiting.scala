package b4processor.modules.fetch

import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3._

/** フェッチモジュールの停止とその理由 */
class Waiting extends Bundle {
  /** 停止しているか */
  val isWaiting = Bool()
  /** 停止理由 */
  val reason = new BranchType.Type
}

object Waiting {
  /** 停止していない状態の信号 */
  def notWaiting(): Waiting = (new Waiting).Lit(_.isWaiting -> false.B, _.reason -> BranchType.None)

  /** btanchTypeによって停止している状態 */
  def waitFor(branchType: BranchType.Type): Waiting = (new Waiting).Lit(_.isWaiting -> true.B, _.reason -> branchType)
}