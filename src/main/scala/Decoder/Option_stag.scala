package Decoder

import chisel3._
import chisel3.util._
import consts.Constants.TAG_WIDTH
import firrtl.Utils.True


class Option_stag extends Module {
  val io = IO(new Bundle {
    val before_dtag = DecoupledIO(UInt(TAG_WIDTH.W))
    val reorder_buffer_dtag = DecoupledIO(UInt(TAG_WIDTH.W))
    val r_value = Output(Bool()) // value選択用回路に用いるRビット
    val stag = Output(UInt(8.W)) // 選択したstagを格納
  })

  /**
   * MuxCaseでstagが見つからない -> 値がレジスタファイルに存在している -> Rbit = 0
   * (otherwise =（before_dtag or reorder_dtagにstagが存在している）-> Rbit = 1)
   */

  io.r_value := Wire(MuxCase(1.U, Seq(
    (io.before_dtag.valid === 0.U && io.reorder_buffer_dtag.valid === 0.U) -> 0.U)))

  /**
   * R=1 ならば，MuxCaseでstagを選択
   */

  when(io.r_value) { // Rbit = 1
    io.stag := RegNext(Mux(io.reorder_buffer_dtag.valid, io.reorder_buffer_dtag.bits, io.before_dtag.bits))
    // 最大発行命令数(i)を増やすならばMux -> MuxCase に変更
  }.otherwise { // Rbit = 0
    io.stag := WireDefault(0.U) // stagが存在しなければstag = 0に設定
  }
}