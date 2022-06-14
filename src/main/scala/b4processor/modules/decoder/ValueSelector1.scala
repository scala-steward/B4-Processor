package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.connections.{DataMemoryOutput, ExecutorOutput}
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
    val executorOutputValue = Vec(params.runParallel, Flipped(new ExecutorOutput))
    val dataMemoryOutputValue = Flipped(new DataMemoryOutput)
    val sourceTag = Input(new SourceTagInfo)
    val value = DecoupledIO(UInt(64.W))
  })

  // 各種DecaoupledIOをreadyにする
  io.reorderBufferValue.ready := true.B

  // ALUからバイパスされた値のうち、destination tagと一致するsource tagを持っている
  val executorMatchingTagExists = Cat((0 until params.runParallel)
    .map { i => io.executorOutputValue(i).valid && io.executorOutputValue(i).destinationTag === io.sourceTag.tag }).orR

  // 値があるか
  io.value.valid := MuxCase(false.B,
    Seq(
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> false.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && executorMatchingTagExists) -> true.B,
      (io.sourceTag.valid && io.dataMemoryOutputValue.valid && io.dataMemoryOutputValue.bits.tag === io.sourceTag.tag) -> true.B,
      (!io.sourceTag.valid) -> true.B,
    ))
  // 値の内容
  io.value.bits := MuxCase(0.U,
    Seq(
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> 0.U,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && executorMatchingTagExists) -> MuxCase(0.U,
        // aluバイパスの中で一致するものの値を取り出す
        (0 until params.runParallel).map(i => (io.executorOutputValue(i).valid && io.executorOutputValue(i).destinationTag === io.sourceTag.tag) -> io.executorOutputValue(i).value)
      ),
      (io.sourceTag.valid && io.dataMemoryOutputValue.valid && io.dataMemoryOutputValue.bits.tag === io.sourceTag.tag) -> io.dataMemoryOutputValue.bits.value,
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}
