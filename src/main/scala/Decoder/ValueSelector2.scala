package Decoder

import chisel3._
import chisel3.util._
import common.OpcodeFormat.{I, J, U}
import common.{OpcodeFormat, OpcodeFormatChecker}
import consts.Constants.TAG_WIDTH

class ValueSelector2(number_of_alus: Int) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(DecoupledIO(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val aluBypassValue = Vec(number_of_alus, Flipped(DecoupledIO(new Bundle {
      val destinationTag = UInt(TAG_WIDTH.W)
      val value = UInt(64.W)
    })))
    val immediateValue = Input(UInt(64.W))
    val opcodeFormat = Input(OpcodeFormat())
    val sourceTag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = DecoupledIO(UInt(64.W))
  })

  io.reorderBufferValue.ready := true.B
  for (i <- 0 until number_of_alus) {
    io.aluBypassValue(i).ready := true.B
  }
  io.sourceTag.ready := true.B

  val aluMatchingTagExists = (0 until number_of_alus)
    .map { i => io.aluBypassValue(i).valid && io.aluBypassValue(i).bits.destinationTag === io.sourceTag.bits }
    .fold(false.B) { (a, b) => a || b }

  io.value.valid := MuxCase(false.B,
    Seq(
      // I形式である
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> true.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && aluMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B,
    ))
  io.value.bits := MuxCase(0.U,
    Seq(
      // I形式である
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> io.immediateValue,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && aluMatchingTagExists) -> MuxCase(0.U,
        (0 until number_of_alus).map(i => (io.aluBypassValue(i).valid && io.aluBypassValue(i).bits.destinationTag === io.sourceTag.bits) -> io.aluBypassValue(i).bits.value)
      ),
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}
