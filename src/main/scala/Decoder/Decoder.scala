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
    val reorderBuffer = new Decoder2ReorderBuffer()
    val reservationStation = DecoupledIO(new Decoder2ReservationStationEntry)
    val decodersBefore = Input(Vec(instruction_offset, new Decoder2NextDecoder))
    val decodersAfter = Output(Vec(instruction_offset + 1, new Decoder2NextDecoder))
  })

  val instRd = io.imem.bits.instruction(11, 7)
  val instRs1 = io.imem.bits.instruction(19, 15)
  val instRs2 = io.imem.bits.instruction(24, 20)
  val instFunct3 = io.imem.bits.instruction(14, 12)
  val instFunct7 = io.imem.bits.instruction(31, 25)
  val instOp = io.imem.bits.instruction(6,0)
  val instImmI = io.imem.bits.instruction(31, 20)

  // リオーダバッファへ
  io.reorderBuffer.programCounter := io.imem.bits.program_counter
  io.reorderBuffer.source1.sourceRegister := instRs1
  io.reorderBuffer.source2.sourceRegister := instRs2
  io.reorderBuffer.destination.destinationRegister.bits := instRd
  // TODO: 命令形式によって変更する
  io.reorderBuffer.destination.destinationRegister.valid := true.B

  // リオーダバッファから一致するタグを取得する
  val stag_selector1 = Module(new StagSelector(instruction_offset))
  stag_selector1.io.stag.ready := true.B
  stag_selector1.io.reorderBufferDtag <> io.reorderBuffer.source1.matchingTag
  stag_selector1.io.beforeDtag <> io.decodersBefore
  val stag1 = stag_selector1.io.stag
  val stag_selector2 = Module(new StagSelector(instruction_offset))
  stag_selector2.io.stag.ready := true.B
  stag_selector2.io.reorderBufferDtag <> io.reorderBuffer.source2.matchingTag
  stag_selector2.io.beforeDtag <> io.decodersBefore
  val stag2 = stag_selector1.io.stag

  // 前のデコーダから次のデコーダへ
  for (i <- 0 until instruction_offset) {
    io.decodersAfter(i) <> io.decodersBefore(i)
  }
  // TODO: 命令形式によって変更する
  io.decodersAfter(instruction_offset).destinationTag := 1.U
  io.decodersAfter(instruction_offset).destinationRegister := 1.U
  io.decodersAfter(instruction_offset).valid := false.B

  // TODO: 別の信号へつなぐ
  io.reorderBuffer.source1.matchingTag.ready := true.B
  io.reorderBuffer.source1.value.ready := true.B
  io.reorderBuffer.source2.matchingTag.ready := true.B
  io.reorderBuffer.source2.value.ready := true.B

  io.imem.ready <> io.reservationStation.ready
  io.imem.valid <> io.reservationStation.valid

  // TODO:  リザベーションステーションにすべてのフィールドを埋める
  val rs = io.reservationStation.bits
  rs.op_code := instOp
  rs.function3 := instFunct3
  rs.immediateOrFunction7 := 0.U
  rs.sourceTag1 := 0.U
  rs.sourceTag2 := 0.U
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
  class SourceRegister extends Bundle {
    val sourceRegister = Output(UInt(5.W))
    val matchingTag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = Flipped(DecoupledIO(UInt(64.W)))
  }

  class DestinationRegister extends Bundle {
    val destinationRegister = DecoupledIO(UInt(5.W))
    val destinationTag = Input(UInt(TAG_WIDTH.W))
  }

  val source1 = new SourceRegister()
  val source2 = new SourceRegister()
  val destination = new DestinationRegister()
  val programCounter = Output(UInt(64.W))
}

class Decoder2NextDecoder extends Bundle {
  val valid = Bool()
  val destinationTag = UInt(5.W)
  val destinationRegister = UInt(TAG_WIDTH.W)
}

class Decoder2ReservationStationEntry extends Bundle {
  val op_code = UInt(7.W)
  val function3 = UInt(3.W)
  val immediateOrFunction7 = UInt(12.W)
  val sourceTag1 = UInt(TAG_WIDTH.W)
  val ready1 = Bool()
  val value1 = UInt(64.W)
  val sourceTag2 = UInt(TAG_WIDTH.W)
  val ready2 = Bool()
  val value2 = UInt(64.W)
}