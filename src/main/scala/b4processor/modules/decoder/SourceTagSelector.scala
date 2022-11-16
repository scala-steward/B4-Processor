package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

/** sourceTag選択回路
  *
  * @param instructionOffset
  *   基準から何個目の命令を処理しているか
  * @param params
  *   パラメータ
  */
class SourceTagSelector(instructionOffset: Int)(implicit params: Parameters)
    extends Module {
  val io = IO(new Bundle {
    val beforeDestinationTag =
      Vec(instructionOffset, Flipped(Valid(new Tag)))
    val reorderBufferDestinationTag =
      Flipped(Valid(new Tag))
    val sourceTag = Output(new SourceTagInfo) // 選択したsource tagを格納
  })

  // reorderBufferのvalidビットを別変数に取り出しておく
  val reorderBufferValid = io.reorderBufferDestinationTag.valid
  // beforeのdestinationTagのvalidビット全てにORをかけたものを取り出す
  val beforeDestinationRegisterValid = Cat(io.beforeDestinationTag.map { d =>
    d.valid
  }).orR
  // MuxCaseでソースタグが見つからない -> 値がレジスタファイルに存在している -> valid = 0
  // (otherwise =（beforeDestinationRegisterValid or reorderBufferValidにsource tagが存在している）-> valid = 1)
  // 1になる条件だけで記述できる
  io.sourceTag.valid := beforeDestinationRegisterValid || reorderBufferValid
  io.sourceTag.from := Mux(
    beforeDestinationRegisterValid,
    SourceTagFrom.BeforeDecoder,
    SourceTagFrom.ReorderBuffer
  )

  // valid=1 ならば，Muxでsource tagを選択
  when(io.sourceTag.valid) {
    io.sourceTag.tag := Mux(
      // beforeDestinationTagを優先して分岐
      beforeDestinationRegisterValid,
      // これまですべてのデコーダのうち、validがtrueならばその値を返すというMuxCase。第二引数の値は配列になっている。
      MuxCase(
        Tag(0), // デフォルト値0
        // これまでのdestinationTagを遡っていき(reverse)、validなものを選ぶ
        (0 until instructionOffset).reverse.map(i =>
          io.beforeDestinationTag(i).valid -> io.beforeDestinationTag(i).bits
        )
      ),
      // リオーダバッファの値
      io.reorderBufferDestinationTag.bits
    )
    // 最大発行命令数(i)を増やすならばMux -> MuxCase に変更
  }.otherwise { // valid = 0
    io.sourceTag.tag := Tag(0) // source tagが存在しなければsourceTag = 0に設定
  }
}
