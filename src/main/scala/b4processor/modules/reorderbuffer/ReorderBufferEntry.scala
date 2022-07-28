package b4processor.modules.reorderbuffer

import b4processor.Parameters
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.ChiselEnum

class ReorderBufferEntry(implicit params: Parameters) extends Bundle {

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

  /** 予測ステータス */
  val prediction = new PredictionStatus.Type

  /** 分岐ID */
  val branchID = UInt(params.branchBufferSize.W)
}

object ReorderBufferEntry {
  def default(implicit params: Parameters): ReorderBufferEntry = {
    val w = Wire(new ReorderBufferEntry)
    w := DontCare
    w.destinationRegister := 0.U
    w
  }
}
