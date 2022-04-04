package Decoder

import chisel3._
import chisel3.util._
import consts.Constants.TAG_WIDTH


class StagSelector(instruction_offset: Int) extends Module {
  val io = IO(new Bundle {
    val beforeDtag = Vec(instruction_offset, Flipped(DecoupledIO(UInt(TAG_WIDTH.W))))
    val reorderBufferDtag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val stag = DecoupledIO(UInt(8.W)) // 選択したstagを格納
  })

  // すべての入力をReadyにする
  for (i <- 0 until instruction_offset) {
    io.beforeDtag(i).ready := true.B
  }
  io.reorderBufferDtag.ready := true.B


  // reorderBufferのvalidビットを別変数に取り出しておく
  val reorderBufferValid = io.reorderBufferDtag.valid
  // beforeDtagのvalidビット全てにORをかけたものを取り出す
  // 読み方
  // beforeDtagのそれぞれの値のvalidを取り出し、一つずつORをかけていく
  // できれば.asUInt.orRが使いたいが、うまく使う方法がわからなかったのでゴリ押し
  val beforeDragValid = io.beforeDtag.map { d => d.valid }.fold(false.B) { (a, b) => a | b }.asBool

  // MuxCaseでstagが見つからない -> 値がレジスタファイルに存在している -> Rbit = 0
  // (otherwise =（before_dtag or reorder_dtagにstagが存在している）-> Rbit = 1)
  io.stag.valid := beforeDragValid || reorderBufferValid

  // valid=1 ならば，Muxでstagを選択
  when(io.stag.valid) {
    io.stag.bits := Mux(
      // beforeDtagを優先して分岐
      beforeDragValid,
      // これまですべてのデコーダのうち、validがtrueならばその値を返すというMuxCase。第二引数の値は配列になっている。
      MuxCase(
        0.U, // デフォルト値0
        (0 until instruction_offset).map(i => io.beforeDtag(i).valid -> io.beforeDtag(i).bits)),
      // リオーダバッファの値
      io.reorderBufferDtag.bits,
    )
    // 最大発行命令数(i)を増やすならばMux -> MuxCase に変更
  }.otherwise { // valid = 0
    io.stag.bits := 0.U // stagが存在しなければstag = 0に設定
  }
}