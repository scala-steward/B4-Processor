package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.common.OpcodeFormat
import b4processor.common.OpcodeFormat._
import b4processor.connections.{CollectedOutput, ResultType}
import chisel3._
import chisel3.util._

/** ソースタグ2の値を選択する回路 基本的にValueSelector1と同じだが、即値の入力を持っている。
  *
  * @param params
  *   パラメータ
  */
class ValueSelector2(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(Valid(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val outputCollector = Flipped(new CollectedOutput)
    val immediateValue = Input(SInt(64.W))
    val opcodeFormat = Input(OpcodeFormat())
    val sourceTag = Input(new SourceTagInfo)
    val value = Valid(UInt(64.W))
  })

  private val o = io.outputCollector.outputs
  val outputMatchingTagExists =
    o.valid && o.bits.resultType === ResultType.Result && o.bits.tag === io.sourceTag.tag

  io.value.valid := MuxCase(
    false.B,
    Seq(
      // I形式である(即値優先)
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> true.B,
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> false.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && outputMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B
    )
  )
  io.value.bits := MuxCase(
    0.U,
    Seq(
      // I形式である(即値優先)
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> io.immediateValue.asUInt,
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> 0.U,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && outputMatchingTagExists) -> o.bits.value,
      (!io.sourceTag.valid) -> io.registerFileValue
    )
  )
}
