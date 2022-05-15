package b4processor.modules.decoder

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * sourceTag選択回路
 *
 * @param instruction_offset 基準から何個目の敬礼を処理しているか
 * @param params             パラメータ
 */
class SourceTagSelector(instruction_offset: Int)(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val beforeDestinationTag = Vec(instruction_offset, Flipped(DecoupledIO(UInt(params.tagWidth.W))))
    val reorderBufferDestinationTag = Flipped(DecoupledIO(UInt(params.tagWidth.W)))
    val sourceTag = DecoupledIO(UInt(8.W)) // 選択したsource tagを格納
  })

  // すべての入力をReadyにする
  for (i <- 0 until instruction_offset) {
    io.beforeDestinationTag(i).ready := true.B
  }
  io.reorderBufferDestinationTag.ready := true.B


  // reorderBufferのvalidビットを別変数に取り出しておく
  val reorderBufferValid = io.reorderBufferDestinationTag.valid
  // beforeのdestinationTagのvalidビット全てにORをかけたものを取り出す
  // 読み方
  // beforeのdestinationTagのそれぞれの値のvalidを取り出し、一つずつORをかけていく
  // できれば.asUInt.orRが使いたいが、うまく使う方法がわからなかったのでゴリ押し
  val beforeDestinationRegisterValid = io.beforeDestinationTag.map { d => d.valid }.fold(false.B) { (a, b) => a || b }

  // MuxCaseでソースタグが見つからない -> 値がレジスタファイルに存在している -> valid = 0
  // (otherwise =（beforeDestinationRegisterValid or reorderBufferValidにsource tagが存在している）-> valid = 1)
  // 1になる条件だけで記述できる
  io.sourceTag.valid := beforeDestinationRegisterValid || reorderBufferValid

  // valid=1 ならば，Muxでsource tagを選択
  when(io.sourceTag.valid) {
    io.sourceTag.bits := Mux(
      // beforeDestinationTagを優先して分岐
      beforeDestinationRegisterValid,
      // これまですべてのデコーダのうち、validがtrueならばその値を返すというMuxCase。第二引数の値は配列になっている。
      MuxCase(
        0.U, // デフォルト値0
        // これまでのdestinationTagを遡っていき(reverse)、validなものを選ぶ
        (0 until instruction_offset).reverse.map(i => io.beforeDestinationTag(i).valid -> io.beforeDestinationTag(i).bits)),
      // リオーダバッファの値
      io.reorderBufferDestinationTag.bits,
    )
    // 最大発行命令数(i)を増やすならばMux -> MuxCase に変更
  }.otherwise { // valid = 0
    io.sourceTag.bits := 0.U // source tagが存在しなければsourceTag = 0に設定
  }
}