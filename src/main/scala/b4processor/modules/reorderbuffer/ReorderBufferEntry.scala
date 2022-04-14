package b4processor.modules.reorderbuffer

import chisel3._
import chisel3.util._

class ReorderBufferEntry extends Bundle {
  /** プログラムカウンタ */
  val programCounter = UInt(64.W)
  /** デスティネーションレジスタ */
  val destinationRegister = UInt(5.W)
  /** 命令の処理が完了した（コミットできる） */
  val ready = Bool()
  /** 実行結果の値 */
  val value = UInt(64.W)
}
