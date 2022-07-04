package b4processor.modules.fetch

import chisel3.experimental.ChiselEnum

/** フェッチ用分岐の種類 */
object BranchType extends ChiselEnum {

  /** 分岐なし */
  val None = Value

  /** 分岐命令 */
  val Branch = Value

  /** JAL命令による強制分岐 */
  val JAL = Value

  /** JALRによる分岐 */
  val JALR = Value

  /** Fenceによる停止 */
  val Fence = Value

  /** Fense.iによる停止 */
  val FenceI = Value
}
