package Decoder

import chisel3._
import chisel3.util._
import consts.Constants.TAG_WIDTH

/**
 * デコーダ
 *
 * @param instruction_offset 同時に扱う命令のうちいくつ目の命令を担当するか
 */
class Decoder(instruction_offset: Int) extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(new IMem2DecoderConnection())
    val reorder_buffer = new Decoder2ReorderBuffer()
  })

  val rd = io.imem.instruction(11, 7)
  val rs1 = io.imem.instruction(19, 15)
  val rs2 = io.imem.instruction(24, 20)
  val funct3 = io.imem.instruction(14, 12)
  val funct7 = io.imem.instruction(31, 25)
  val imm_I = io.imem.instruction(31, 20)

  // to reorder buffer
  io.reorder_buffer.program_counter := io.imem.program_counter
  io.reorder_buffer.source1.register_source := rs1
  io.reorder_buffer.source2.register_source := rs2
  io.reorder_buffer.destination.register_destination := rd

  // TODO: change
  io.reorder_buffer.source1.matching_tag.ready := true.B
  io.reorder_buffer.source1.value.ready := true.B
  io.reorder_buffer.source2.matching_tag.ready := true.B
  io.reorder_buffer.source2.value.ready := true.B
}

class IMem2DecoderConnection extends Bundle {
  val instruction = Output(UInt(64.W))
  val program_counter = Output(UInt(64.W))
}

class Decoder2ReorderBuffer extends Bundle {
  class RegisterSource extends Bundle {
    val register_source = Output(UInt(5.W))
    val matching_tag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = Flipped(DecoupledIO(UInt(64.W)))
  }

  class RegisterDestination extends Bundle {
    val register_destination = Output(UInt(5.W))
    val destination_tag = Input(UInt(TAG_WIDTH.W))
  }

  val source1 = new RegisterSource()
  val source2 = new RegisterSource()
  val destination = new RegisterDestination()
  val program_counter = Output(UInt(64.W))
}
