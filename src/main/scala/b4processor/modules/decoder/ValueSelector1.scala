package b4processor.modules.decoder

import b4processor.Constants.TAG_WIDTH
import chisel3._
import chisel3.util._

/**
 * ソースタグ1の値を選択する回路
 *
 * @param number_of_alus ALUの数
 */
class ValueSelector1(number_of_alus: Int) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(DecoupledIO(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val aluBypassValue = Vec(number_of_alus, Flipped(DecoupledIO(new Bundle {
      val destinationTag = UInt(TAG_WIDTH.W)
      val value = UInt(64.W)
    })))
    val sourceTag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = DecoupledIO(UInt(64.W))
  })

  // 各種DecaoupledIOをreadyにする
  io.reorderBufferValue.ready := true.B
  for (i <- 0 until number_of_alus)
    io.aluBypassValue(i).ready := true.B
  io.sourceTag.ready := true.B

  // ALUからバイパスされた値のうち、destination tagと一致するsource tagを持っている
  val aluMatchingTagExists = (0 until number_of_alus)
    .map { i => io.aluBypassValue(i).valid && io.aluBypassValue(i).bits.destinationTag === io.sourceTag.bits }
    .fold(false.B) { (a, b) => a || b }

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
        (0 until number_of_alus).map(i => (io.aluBypassValue(i).valid && io.aluBypassValue(i).bits.destinationTag === io.sourceTag.bits) -> io.aluBypassValue(i).bits.value)
      ),
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}
