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
    val imem = Flipped(DecoupledIO(new IMem2DecoderConnection()))
    val reorder_buffer = new Decoder2ReorderBuffer()
    val reservationStation = DecoupledIO(new Decoder2ReservationStationEntry)
    val previous_decoders = Input(Vec(instruction_offset, new Decoder2NextDecoder))
    val next_decoders = Output(Vec(instruction_offset + 1, new Decoder2NextDecoder))
  })

  val inst_rd = io.imem.bits.instruction(11, 7)
  val inst_rs1 = io.imem.bits.instruction(19, 15)
  val inst_rs2 = io.imem.bits.instruction(24, 20)
  val inst_funct3 = io.imem.bits.instruction(14, 12)
  val inst_funct7 = io.imem.bits.instruction(31, 25)
  val inst_op = io.imem.bits.instruction(6,0)
  val inst_imm_I = io.imem.bits.instruction(31, 20)

  // to reorder buffer
  io.reorder_buffer.program_counter := io.imem.bits.program_counter
  io.reorder_buffer.source1.register_source := inst_rs1
  io.reorder_buffer.source2.register_source := inst_rs2
  io.reorder_buffer.destination.register_destination.bits := inst_rd
  // TODO: change to depend on instruction type
  io.reorder_buffer.destination.register_destination.valid := true.B

  // get matching tags
  val option_stag = Module(new Option_stag)
  option_stag.io.reorder_buffer_dtag <> io.reorder_buffer.source1.matching_tag

  // previous decoder to next decoder
  for (i <- 0 until instruction_offset) {
    io.next_decoders(i) <> io.previous_decoders(i)
  }
  // TODO: apply same logic to valid as reorder buffer destination register
  io.next_decoders(instruction_offset).destination_tag := 1.U
  io.next_decoders(instruction_offset).destination_register := 1.U
  io.next_decoders(instruction_offset).valid := false.B

  // TODO: change
  io.reorder_buffer.source1.matching_tag.ready := true.B
  io.reorder_buffer.source1.value.ready := true.B
  io.reorder_buffer.source2.matching_tag.ready := true.B
  io.reorder_buffer.source2.value.ready := true.B

  io.imem.ready <> io.reservationStation.ready
  io.imem.valid <> io.reservationStation.valid

  // TODO: fill all reservation station fields
  val rs = io.reservationStation.bits
  rs.op_code := inst_op
  rs.function3 := inst_funct3
  rs.immediate_or_function7 := 0.U
  rs.source_tag1 := 0.U
  rs.source_tag2 := 0.U
  rs.ready1 := false.B
  rs.ready2 := false.B
  rs.value1 := 0.U
  rs.value2 := 0.U
}

class IMem2DecoderConnection extends Bundle {
  val instruction = UInt(64.W)
  val program_counter = UInt(64.W)
}

class Decoder2ReorderBuffer extends Bundle {
  class RegisterSource extends Bundle {
    val register_source = Output(UInt(5.W))
    val matching_tag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = Flipped(DecoupledIO(UInt(64.W)))
  }

  class RegisterDestination extends Bundle {
    val register_destination = DecoupledIO(UInt(5.W))
    val destination_tag = Input(UInt(TAG_WIDTH.W))
  }

  val source1 = new RegisterSource()
  val source2 = new RegisterSource()
  val destination = new RegisterDestination()
  val program_counter = Output(UInt(64.W))
}

class Decoder2NextDecoder extends Bundle {
  val valid = Bool()
  val destination_tag = UInt(5.W)
  val destination_register = UInt(TAG_WIDTH.W)
}

class Decoder2ReservationStationEntry extends Bundle {
  val op_code = UInt(7.W)
  val function3 = UInt(3.W)
  val immediate_or_function7 = UInt(12.W)
  val source_tag1 = UInt(TAG_WIDTH.W)
  val ready1 = Bool()
  val value1 = UInt(64.W)
  val source_tag2 = UInt(TAG_WIDTH.W)
  val ready2 = Bool()
  val value2 = UInt(64.W)
}