package common

import chisel3.experimental.ChiselEnum
import chisel3._
import chisel3.util._

object OpcodeFormat extends ChiselEnum {
  val R, I, J, S, U, B = Value
}

class OpcodeFormatChecker extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(7.W))
    val format = Output(OpcodeFormat())
  })

  // TODO: しっかりした確認
  io.format := MuxCase(OpcodeFormat.R, Seq(
    (io.opcode === BitPat("b00??011") || io.opcode === "b0001111".U) -> OpcodeFormat.I
  ))
}
