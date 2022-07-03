package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.connections.CollectedOutput
import chisel3._
import chisel3.util._

/**
 * ソースタグ1の値を選択する回路
 *
 * @param params パラメータ
 */
class ValueSelector1(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(DecoupledIO(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val outputCollector = Flipped(new CollectedOutput)
    val sourceTag = Input(new SourceTagInfo)
    val value = DecoupledIO(UInt(64.W))
  })

  // 各種DecaoupledIOをreadyにする
  io.reorderBufferValue.ready := true.B

  // ALUからバイパスされた値のうち、destination tagと一致するsource tagを持っている
  val outputMatching = Cat(io.outputCollector.outputs.map(o => o.validAsResult && o.tag === io.sourceTag.tag).reverse)
  val outputMatchingTagExists = outputMatching.orR

  // 値があるか
  io.value.valid := MuxCase(false.B,
    Seq(
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> false.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && outputMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B,
    ))
  // 値の内容
  io.value.bits := MuxCase(0.U,
    Seq(
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> 0.U,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && outputMatchingTagExists) ->
        // Mux1Hは入力が一つであることが求められるがタグ一つにつき出力は一つなので問題ない
        Mux1H(outputMatching.asBools.zip(io.outputCollector.outputs).map { case (flag, output) => flag -> output.value }),
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}
