package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.connections.ExecutionRegisterBypass
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
    val aluBypassValue = Vec(params.numberOfALUs, Flipped(new ExecutionRegisterBypass))
    val sourceTag = Flipped(DecoupledIO(UInt(params.tagWidth.W)))
    val value = DecoupledIO(UInt(64.W))
  })

  // 各種DecaoupledIOをreadyにする
  io.reorderBufferValue.ready := true.B
  io.sourceTag.ready := true.B

  // ALUからバイパスされた値のうち、destination tagと一致するsource tagを持っている
  val aluMatchingTagExists = Cat((0 until params.numberOfALUs)
    .map { i => io.aluBypassValue(i).valid && io.aluBypassValue(i).destinationTag === io.sourceTag.bits }).orR

  // 値があるか
  io.value.valid := MuxCase(false.B,
    Seq(
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && aluMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B,
    ))
  // 値の内容
  io.value.bits := MuxCase(0.U,
    Seq(
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && aluMatchingTagExists) -> MuxCase(0.U,
        // aluバイパスの中で一致するものの値を取り出す
        (0 until params.numberOfALUs).map(i => (io.aluBypassValue(i).valid && io.aluBypassValue(i).destinationTag === io.sourceTag.bits) -> io.aluBypassValue(i).value)
      ),
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}
