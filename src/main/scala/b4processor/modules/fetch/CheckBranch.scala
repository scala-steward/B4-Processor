package b4processor.modules.fetch

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util._


/** 命令の種類のチェック */
class CheckBranch extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(32.W))
    val branchType = Output(BranchType())
    val offset = Output(SInt(21.W))
  })

  val opcode = io.instruction(6, 0)

  io.offset := MuxLookup(opcode, 4.S, Seq(
    // jalr
    "b1100111".U -> Cat(io.instruction(31, 20), 0.U(1.W)).asSInt,
    // jal
    "b1101111".U -> Cat(io.instruction(31), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21), 0.U(1.W)).asSInt,
    // branch
    "b1100011".U -> Cat(io.instruction(31), io.instruction(7), io.instruction(30, 25), io.instruction(11, 8), 0.U(1.W)).asSInt,
  ))

  io.branchType := MuxLookup(opcode, BranchType.None, Seq(
    // jalr
    "b1100111".U -> BranchType.JALR,
    // jal
    "b1101111".U -> BranchType.JAL,
    // B
    "b1100011".U -> BranchType.Branch,
  ))
}

object BranchType extends ChiselEnum {
  val None = Value
  val Branch = Value
  val JAL = Value
  val JALR = Value
}

object CheckBranch extends App {
  (new ChiselStage).emitVerilog(new CheckBranch)
}