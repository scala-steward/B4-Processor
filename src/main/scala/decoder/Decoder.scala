package decoder

import chisel3._
import chisel3.util._
import common.OpcodeFormat._
import common.OpcodeFormatChecker
import connections._

/**
 * デコーダ
 *
 * @param instruction_offset 同時に扱う命令のうちいくつ目の命令を担当するか
 */
class Decoder(instruction_offset: Int, number_of_alus: Int) extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(DecoupledIO(new IMem2Decoder()))
    val reorderBuffer = new Decoder2ReorderBuffer()
    val alu = Vec(number_of_alus, Flipped(DecoupledIO(new ALU2Decoder)))
    val registerFile = new Decoder2RegisterFile

    val decodersBefore = Input(Vec(instruction_offset, new Decoder2NextDecoder))
    val decodersAfter = Output(Vec(instruction_offset + 1, new Decoder2NextDecoder))

    val reservationStation = DecoupledIO(new Decoder2ReservationStationEntry)
  })

  val instRd = io.imem.bits.instruction(11, 7)
  val instRs1 = io.imem.bits.instruction(19, 15)
  val instRs2 = io.imem.bits.instruction(24, 20)
  val instFunct3 = io.imem.bits.instruction(14, 12)
  val instFunct7 = io.imem.bits.instruction(31, 25)
  val instOp = io.imem.bits.instruction(6, 0)
  val instImmI = io.imem.bits.instruction(31, 20)
  val instImmS = Cat(io.imem.bits.instruction(31, 25), io.imem.bits.instruction(11, 7))
  val instImmB = Cat(io.imem.bits.instruction(31), io.imem.bits.instruction(7), io.imem.bits.instruction(30, 25), io.imem.bits.instruction(11, 8))
  val instImmU = io.imem.bits.instruction(31, 12)
  val instImmJ = Cat(io.imem.bits.instruction(31), io.imem.bits.instruction(19, 12), io.imem.bits.instruction(20), io.imem.bits.instruction(30, 21))

  val immIExtended = Mux(instImmI(11), (!0.U(64.W)) & instImmI, 0.U(64.W) | instImmI)
  val immUExtended = Cat(instImmU, 0.U(12.W))
  val immJExtended = Mux(instImmU(19), (!0.U(64.W)) & Cat(instImmJ, 0.U(1.W)), 0.U(64.W) | Cat(instImmJ, 0.U(1.W)))

  val opcodeFormatChecker = Module(new OpcodeFormatChecker)
  opcodeFormatChecker.io.opcode := instOp

  val destinationIsValid = opcodeFormatChecker.io.format === R ||
    opcodeFormatChecker.io.format === I ||
    opcodeFormatChecker.io.format === U ||
    opcodeFormatChecker.io.format === J

  // リオーダバッファへ
  io.reorderBuffer.programCounter := io.imem.bits.program_counter
  io.reorderBuffer.source1.sourceRegister := instRs1
  io.reorderBuffer.source2.sourceRegister := instRs2
  io.reorderBuffer.destination.destinationRegister.bits := instRd
  io.reorderBuffer.destination.destinationRegister.valid := destinationIsValid

  // レジスタファイルへ
  io.registerFile.sourceRegister1 := instRs1
  io.registerFile.sourceRegister2 := instRs2

  // リオーダバッファから一致するタグを取得する
  // セレクタ1
  val stag_selector1 = Module(new StagSelector(instruction_offset))
  stag_selector1.io.stag.ready := true.B
  stag_selector1.io.reorderBufferDtag <> io.reorderBuffer.source1.matchingTag
  for (i <- 0 until instruction_offset) {
    stag_selector1.io.beforeDtag(i).bits := io.decodersBefore(i).destinationTag
    stag_selector1.io.beforeDtag(i).valid := io.decodersBefore(i).destinationRegister === instRs1
  }
  val stag1 = stag_selector1.io.stag

  // セレクタ2
  val stag_selector2 = Module(new StagSelector(instruction_offset))
  stag_selector2.io.stag.ready := true.B
  stag_selector2.io.reorderBufferDtag <> io.reorderBuffer.source2.matchingTag
  for (i <- 0 until instruction_offset) {
    stag_selector2.io.beforeDtag(i).bits := io.decodersBefore(i).destinationTag
    stag_selector2.io.beforeDtag(i).valid := io.decodersBefore(i).destinationRegister === instRs2
  }
  val stag2 = stag_selector2.io.stag

  // Valueの選択
  // value1
  val valueSelector1 = Module(new ValueSelector1(number_of_alus))
  valueSelector1.io.value.ready := true.B
  valueSelector1.io.sourceTag <> stag1
  valueSelector1.io.reorderBufferValue <> io.reorderBuffer.source1.value
  valueSelector1.io.registerFileValue := io.registerFile.value1
  for (i <- 0 until number_of_alus) {
    valueSelector1.io.aluBypassValue(i) <> io.alu(i)
  }
  // value2
  val valueSelector2 = Module(new ValueSelector2(number_of_alus))
  valueSelector2.io.value.ready := true.B
  valueSelector2.io.sourceTag <> stag2
  valueSelector2.io.reorderBufferValue <> io.reorderBuffer.source2.value
  valueSelector2.io.registerFileValue := io.registerFile.value2
  valueSelector2.io.immediateValue := MuxLookup(opcodeFormatChecker.io.format.asUInt, 0.U, Seq(
    I.asUInt -> immIExtended,
    U.asUInt -> immUExtended,
    J.asUInt -> immJExtended
  ))
  valueSelector2.io.opcodeFormat := opcodeFormatChecker.io.format
  for (i <- 0 until number_of_alus) {
    valueSelector2.io.aluBypassValue(i) <> io.alu(i)
  }

  // 前のデコーダから次のデコーダへ
  for (i <- 0 until instruction_offset) {
    io.decodersAfter(i) <> io.decodersBefore(i)
  }
  // 次のデコーダへ伝える情報
  when(destinationIsValid) {
    io.decodersAfter(instruction_offset).destinationTag := io.reorderBuffer.destination.destinationTag
    io.decodersAfter(instruction_offset).destinationRegister := instRd
    io.decodersAfter(instruction_offset).valid := true.B
  } otherwise {
    io.decodersAfter(instruction_offset).destinationTag := 0.U
    io.decodersAfter(instruction_offset).destinationRegister := 0.U
    io.decodersAfter(instruction_offset).valid := false.B
  }

  io.reorderBuffer.source1.matchingTag.ready := true.B
  io.reorderBuffer.source1.value.ready := true.B
  io.reorderBuffer.source2.matchingTag.ready := true.B
  io.reorderBuffer.source2.value.ready := true.B

  io.imem.ready <> io.reservationStation.ready
  io.imem.valid <> io.reservationStation.valid

  val rs = io.reservationStation.bits
  rs.op_code := instOp
  rs.function3 := instFunct3
  rs.immediateOrFunction7 := MuxLookup(opcodeFormatChecker.io.format.asUInt, 0.U, Seq(
    R.asUInt -> Cat("b000000".U, instFunct7),
    S.asUInt -> instImmS,
    B.asUInt -> instImmB,
  ))
  rs.destinationTag := io.reorderBuffer.destination.destinationTag
  rs.sourceTag1 := stag1.bits
  rs.sourceTag2 := stag2.bits
  rs.ready1 := valueSelector1.io.value.valid
  rs.ready2 := valueSelector2.io.value.valid
  rs.value1 := valueSelector1.io.value.bits
  rs.value2 := valueSelector2.io.value.bits
}