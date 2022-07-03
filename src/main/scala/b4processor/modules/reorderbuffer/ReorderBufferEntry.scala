package b4processor.modules.reorderbuffer

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor

class ReorderBufferEntry extends Bundle {
  /** プログラムカウンタ */
  val programCounter = SInt(64.W)
  /** デスティネーションレジスタ */
  val destinationRegister = UInt(5.W)
  /** 命令の処理が完了した（コミットできる） */
  val valueReady = Bool()
  /** 実行結果の値 */
  val value = UInt(64.W)
  /** 該当のbufferがStore命令かどうか */
  val storeSign = Bool()
}

object ReorderBufferEntry {
  def default: ReorderBufferEntry = (new ReorderBufferEntry).Lit(
    _.value -> 0.U,
    _.valueReady -> false.B,
    _.programCounter -> 0.S,
    _.destinationRegister -> 0.U,
    _.storeSign -> false.B)
}