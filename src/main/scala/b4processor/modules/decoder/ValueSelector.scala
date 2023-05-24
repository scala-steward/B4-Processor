package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.common.OpcodeFormat
import b4processor.common.OpcodeFormat._
import b4processor.connections.{CollectedOutput, ResultType}
import chisel3._
import chisel3.util._

/** ソースタグ1の値を選択する回路
  *
  * @param params
  *   パラメータ
  */
class ValueSelector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(Valid(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val outputCollector = Flipped(new CollectedOutput)
    val sourceTag = Input(new SourceTagInfo)
    val value = Valid(UInt(64.W))
  })

  // ALUからバイパスされた値のうち、destination tagと一致するsource tagを持っている
  private val o = io.outputCollector.outputs
  private val outputMatchingTagExists =
    o.valid && o.bits.resultType === ResultType.Result && o.bits.tag === io.sourceTag.tag

  // 値があるか
  io.value.valid := MuxCase(
    false.B,
    Seq(
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> false.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && outputMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B
    )
  )
  // 値の内容
  io.value.bits := MuxCase(
    0.U,
    Seq(
      (io.sourceTag.from === SourceTagFrom.BeforeDecoder) -> 0.U,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && outputMatchingTagExists) -> o.bits.value,
      (!io.sourceTag.valid) -> io.registerFileValue
    )
  )
}
