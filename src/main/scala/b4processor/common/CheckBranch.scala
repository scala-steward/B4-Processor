package b4processor.common

import chisel3._
import chisel3.util._


/** 命令の種類のチェック */
class CheckBranch extends Module {
  val input = IO(Input(new Bundle {
    val instruction = UInt(32.W)
    val programCounter = SInt(64.W)
  }))
  val output = IO(Output(new Bundle {
    val isBranch = Bool()
    val branchAddress = SInt(64.W)
  }))

  val opcode = input.instruction(6, 0)

  output.branchAddress := MuxLookup(opcode, input.programCounter + 4.S, Seq(
    // jalr
    "b1100111".U -> (input.programCounter
      + Cat(input.instruction(31, 20), 0.U(1.W)).asSInt),
    // jal
    "b1101111".U -> (input.programCounter
      + Cat(input.instruction(31), input.instruction(19, 12), input.instruction(20), input.instruction(30, 21), 0.U(1.W)).asSInt),
    // branch
    "b1100011".U -> (input.programCounter
      + Cat(input.instruction(31), input.instruction(7), input.instruction(30, 25), input.instruction(11, 8), 0.U(1.W)).asSInt),
  ))

  output.isBranch := MuxLookup(opcode, false.B, Seq(
    "b1100111".U -> true.B,
    "b1101111".U -> true.B,
    "b1100011".U -> true.B,
  ))
}
