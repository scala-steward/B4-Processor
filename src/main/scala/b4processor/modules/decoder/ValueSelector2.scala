package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.common.OpcodeFormat
import b4processor.common.OpcodeFormat._
import b4processor.connections.ExecutorOutput
import chisel3._
import chisel3.util._

/**
 * ソースタグ2の値を選択する回路
 * 基本的にValueSelector1と同じだが、即値の入力を持っている。
 *
 * @param params パラメータ
 */
class ValueSelector2(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(DecoupledIO(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val executorBypassValue = Vec(params.runParallel, Flipped(new ExecutorOutput))
    val immediateValue = Input(SInt(64.W))
    val opcodeFormat = Input(OpcodeFormat())
    val sourceTag = Input(new SourceTagInfo)
    val value = DecoupledIO(UInt(64.W))
  })

  io.reorderBufferValue.ready := true.B

  val executorMatchingTagExists = Cat((0 until params.runParallel)
    .map { i => io.executorBypassValue(i).valid && io.executorBypassValue(i).destinationTag === io.sourceTag.tag }).orR

  io.value.valid := MuxCase(false.B,
    Seq(
      // I形式である(即値優先)
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> true.B,
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> false.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && executorMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B,
    ))
  io.value.bits := MuxCase(0.U,
    Seq(
      // I形式である(即値優先)
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> io.immediateValue.asUInt,
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> 0.U,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && executorMatchingTagExists) -> MuxCase(0.U,
        (0 until params.runParallel).map(i => (io.executorBypassValue(i).valid && io.executorBypassValue(i).destinationTag === io.sourceTag.tag) -> io.executorBypassValue(i).value)
      ),
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}
