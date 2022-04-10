package b4processor.modules.decoder

import b4processor.common.OpcodeFormat._
import b4processor.common.OpcodeFormatChecker
import b4processor.connections.{ExecutionRegisterBypass, _}
import b4processor.modules.reservationstation.ReservationStationEntry
import chisel3._
import chisel3.util._

/**
 * デコーダ
 *
 * @param instructionOffset 同時に扱う命令のうちいくつ目の命令を担当するか
 * @param numberOfAlus      ALUの数
 */
class Decoder(instructionOffset: Int, numberOfAlus: Int) extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(new IMem2Decoder())
    val reorderBuffer = new Decoder2ReorderBuffer()
    val alu = Vec(numberOfAlus, Flipped(new ExecutionRegisterBypass))
    val registerFile = new Decoder2RegisterFile

    val decodersBefore = Input(Vec(instructionOffset, new Decoder2NextDecoder))
    val decodersAfter = Output(Vec(instructionOffset + 1, new Decoder2NextDecoder))

    val reservationStation = DecoupledIO(new ReservationStationEntry)
  })

  // 命令からそれぞれの昨日のブロックを取り出す
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

  // 即値を64bitに符号拡張
  val immIExtended = Mux(instImmI(11), (!0.U(64.W)) & instImmI, 0.U(64.W) | instImmI)
  val immUExtended = Cat(instImmU, 0.U(12.W))
  val immJExtended = Mux(instImmU(19), (!0.U(64.W)) & Cat(instImmJ, 0.U(1.W)), 0.U(64.W) | Cat(instImmJ, 0.U(1.W)))

  // オペコードが何形式かを調べるモジュール
  val opcodeFormatChecker = Module(new OpcodeFormatChecker)
  opcodeFormatChecker.io.opcode := instOp

  // デスティネーションレジスタの値が使われるか(rdがない命令ではfalse)
  val destinationIsValid = opcodeFormatChecker.io.format === R ||
    opcodeFormatChecker.io.format === I ||
    opcodeFormatChecker.io.format === U ||
    opcodeFormatChecker.io.format === J

  // リオーダバッファへの入力
  io.reorderBuffer.bits.programCounter := io.imem.bits.program_counter
  io.reorderBuffer.bits.source1.sourceRegister := instRs1
  io.reorderBuffer.bits.source2.sourceRegister := instRs2
  io.reorderBuffer.bits.destination.destinationRegister.bits := instRd
  io.reorderBuffer.bits.destination.destinationRegister.valid := destinationIsValid

  // レジスタファイルへの入力
  io.registerFile.sourceRegister1 := instRs1
  io.registerFile.sourceRegister2 := instRs2

  // リオーダバッファから一致するタグを取得する
  // セレクタ1
  val sourceTagSelector1 = Module(new SourceTagSelector(instructionOffset))
  sourceTagSelector1.io.sourceTag.ready := true.B
  sourceTagSelector1.io.reorderBufferDestinationTag <> io.reorderBuffer.bits.source1.matchingTag
  for (i <- 0 until instructionOffset) {
    // 前のデコーダから流れてきたdestination tag
    sourceTagSelector1.io.beforeDestinationTag(i).bits := io.decodersBefore(i).destinationTag
    // 前のデコーダから流れてきたdestination registerがsource registerと等しいか
    // (もともとvalidは情報が存在するかという意味で使われているが、ここで一致しているかという意味に変換)
    sourceTagSelector1.io.beforeDestinationTag(i).valid := io.decodersBefore(i).destinationRegister === instRs1
  }
  val sourceTag1 = sourceTagSelector1.io.sourceTag
  // セレクタ2
  val sourceTagSelector2 = Module(new SourceTagSelector(instructionOffset))
  sourceTagSelector2.io.sourceTag.ready := true.B
  sourceTagSelector2.io.reorderBufferDestinationTag <> io.reorderBuffer.bits.source2.matchingTag
  for (i <- 0 until instructionOffset) {
    sourceTagSelector2.io.beforeDestinationTag(i).bits := io.decodersBefore(i).destinationTag
    sourceTagSelector2.io.beforeDestinationTag(i).valid := io.decodersBefore(i).destinationRegister === instRs2
  }
  val sourceTag2 = sourceTagSelector2.io.sourceTag

  // Valueの選択
  // value1
  val valueSelector1 = Module(new ValueSelector1(numberOfAlus))
  valueSelector1.io.value.ready := true.B
  valueSelector1.io.sourceTag <> sourceTag1
  valueSelector1.io.reorderBufferValue <> io.reorderBuffer.bits.source1.value
  valueSelector1.io.registerFileValue := io.registerFile.value1
  for (i <- 0 until numberOfAlus) {
    valueSelector1.io.aluBypassValue(i) <> io.alu(i)
  }
  // value2
  val valueSelector2 = Module(new ValueSelector2(numberOfAlus))
  valueSelector2.io.value.ready := true.B
  valueSelector2.io.sourceTag <> sourceTag2
  valueSelector2.io.reorderBufferValue <> io.reorderBuffer.bits.source2.value
  valueSelector2.io.registerFileValue := io.registerFile.value2
  valueSelector2.io.immediateValue := MuxLookup(opcodeFormatChecker.io.format.asUInt, 0.U, Seq(
    I.asUInt -> immIExtended,
    U.asUInt -> immUExtended,
    J.asUInt -> immJExtended
  ))
  valueSelector2.io.opcodeFormat := opcodeFormatChecker.io.format
  for (i <- 0 until numberOfAlus) {
    valueSelector2.io.aluBypassValue(i) <> io.alu(i)
  }

  // 前のデコーダから次のデコーダへ
  for (i <- 0 until instructionOffset) {
    io.decodersAfter(i) <> io.decodersBefore(i)
  }
  // 次のデコーダへ伝える情報
  when(destinationIsValid) {
    io.decodersAfter(instructionOffset).destinationTag := io.reorderBuffer.bits.destination.destinationTag
    io.decodersAfter(instructionOffset).destinationRegister := instRd
    io.decodersAfter(instructionOffset).valid := true.B
  } otherwise {
    io.decodersAfter(instructionOffset).destinationTag := 0.U
    io.decodersAfter(instructionOffset).destinationRegister := 0.U
    io.decodersAfter(instructionOffset).valid := false.B
  }

  // リオーダバッファからの情報は常に受け取ることにする
  io.reorderBuffer.bits.source1.matchingTag.ready := true.B
  io.reorderBuffer.bits.source1.value.ready := true.B
  io.reorderBuffer.bits.source2.matchingTag.ready := true.B
  io.reorderBuffer.bits.source2.value.ready := true.B

  // 命令をデコードするのはリオーダバッファにエントリの空きがあり、リザベーションステーションにも空きがあるとき
  io.imem.ready := io.reservationStation.ready && io.reorderBuffer.ready
  // リオーダバッファやリザベーションステーションに新しいエントリを追加するのは命令がある時
  io.reorderBuffer.valid := io.imem.valid
  io.reservationStation.valid := io.imem.valid

  // RSへの出力を埋める
  val rs = io.reservationStation.bits
  rs.op_code := instOp
  rs.function3 := instFunct3
  rs.immediateOrFunction7 := MuxLookup(opcodeFormatChecker.io.format.asUInt, 0.U, Seq(
    R.asUInt -> Cat("b000000".U, instFunct7),
    S.asUInt -> instImmS,
    B.asUInt -> instImmB,
  ))
  rs.destinationTag := io.reorderBuffer.bits.destination.destinationTag
  rs.sourceTag1 := sourceTag1.bits
  rs.sourceTag2 := sourceTag2.bits
  rs.ready1 := valueSelector1.io.value.valid
  rs.ready2 := valueSelector2.io.value.valid
  rs.value1 := valueSelector1.io.value.bits
  rs.value2 := valueSelector2.io.value.bits
}