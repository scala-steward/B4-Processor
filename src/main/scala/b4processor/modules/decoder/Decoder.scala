package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.common.OpcodeFormat._
import b4processor.common.OpcodeFormatChecker
import b4processor.connections._
import b4processor.modules.reservationstation.ReservationStationEntry
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

/**
 * デコーダ
 *
 * @param instructionOffset 同時に扱う命令のうちいくつ目の命令を担当するか
 * @param params            パラメータ
 */
class Decoder(instructionOffset: Int)(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val instructionFetch = Flipped(new Fetch2Decoder())
    val reorderBuffer = new Decoder2ReorderBuffer
    val executors = Vec(params.runParallel, Flipped(new ExecutionRegisterBypass))
    val registerFile = new Decoder2RegisterFile()

    val decodersBefore = Input(Vec(instructionOffset, new Decoder2NextDecoder))
    val decodersAfter = Output(Vec(instructionOffset + 1, new Decoder2NextDecoder))

    val reservationStation = new Decoder2ReservationStation

    val loadStoreQueue = new Decoder2LoadStoreQueue()
  })

  // 命令からそれぞれの昨日のブロックを取り出す
  val instRd = io.instructionFetch.bits.instruction(11, 7)
  val instRs1 = io.instructionFetch.bits.instruction(19, 15)
  val instRs2 = io.instructionFetch.bits.instruction(24, 20)
  val instFunct3 = io.instructionFetch.bits.instruction(14, 12)
  val instFunct7 = io.instructionFetch.bits.instruction(31, 25)
  val instOp = io.instructionFetch.bits.instruction(6, 0)
  val instImmI = io.instructionFetch.bits.instruction(31, 20)
  val instImmS = Cat(io.instructionFetch.bits.instruction(31, 25), io.instructionFetch.bits.instruction(11, 7))
  val instImmB = Cat(io.instructionFetch.bits.instruction(31), io.instructionFetch.bits.instruction(7), io.instructionFetch.bits.instruction(30, 25), io.instructionFetch.bits.instruction(11, 8))
  val instImmU = io.instructionFetch.bits.instruction(31, 12)
  val instImmJ = Cat(io.instructionFetch.bits.instruction(31), io.instructionFetch.bits.instruction(19, 12), io.instructionFetch.bits.instruction(20), io.instructionFetch.bits.instruction(30, 21))

  // 即値を64bitに符号拡張
  val immIExtended = instImmI.asSInt
  val immUExtended = Cat(instImmU, 0.U(12.W)).asSInt
  val immJExtended = Cat(instImmJ, 0.U(1.W)).asSInt

  // オペコードが何形式かを調べるモジュール
  val opcodeFormatChecker = Module(new OpcodeFormatChecker)
  opcodeFormatChecker.io.opcode := instOp

  // デスティネーションレジスタの値が使われるか(rdがない命令ではfalse)
  val destinationIsValid = opcodeFormatChecker.io.format === R ||
    opcodeFormatChecker.io.format === I ||
    opcodeFormatChecker.io.format === U ||
    opcodeFormatChecker.io.format === J

  // ソースレジスタ1に値があるかどうか
  val source1IsValid = opcodeFormatChecker.io.format === R ||
    opcodeFormatChecker.io.format === I ||
    opcodeFormatChecker.io.format === S ||
    opcodeFormatChecker.io.format === B

  // ソースレジスタ2に値があるかどうか
  val source2IsValid = opcodeFormatChecker.io.format === R ||
    opcodeFormatChecker.io.format === S ||
    opcodeFormatChecker.io.format === B

  // リオーダバッファへの入力
  io.reorderBuffer.programCounter := io.instructionFetch.bits.programCounter
  io.reorderBuffer.source1.sourceRegister := Mux(source1IsValid,
    instRs1,
    0.U)
  io.reorderBuffer.source2.sourceRegister := Mux(source2IsValid,
    instRs2,
    0.U)
  io.reorderBuffer.destination.destinationRegister := Mux(destinationIsValid,
    instRd,
    0.U)

  // レジスタファイルへの入力
  io.registerFile.sourceRegister1 := instRs1
  io.registerFile.sourceRegister2 := instRs2

  // リオーダバッファから一致するタグを取得する
  // セレクタ1
  val sourceTagSelector1 = Module(new SourceTagSelector(instructionOffset))
  sourceTagSelector1.io.reorderBufferDestinationTag <> io.reorderBuffer.source1.matchingTag
  for (i <- 0 until instructionOffset) {
    // 前のデコーダから流れてきたdestination tag
    sourceTagSelector1.io.beforeDestinationTag(i).bits := io.decodersBefore(i).destinationTag
    // 前のデコーダから流れてきたdestination registerがsource registerと等しいか
    // (もともとvalidは情報が存在するかという意味で使われているが、ここで一致しているかという意味に変換)
    sourceTagSelector1.io.beforeDestinationTag(i).valid := io.decodersBefore(i).destinationRegister === instRs1 && io.decodersBefore(i).valid
  }
  val sourceTag1 = sourceTagSelector1.io.sourceTag
  // セレクタ2
  val sourceTagSelector2 = Module(new SourceTagSelector(instructionOffset))
  sourceTagSelector2.io.reorderBufferDestinationTag <> io.reorderBuffer.source2.matchingTag
  for (i <- 0 until instructionOffset) {
    sourceTagSelector2.io.beforeDestinationTag(i).bits := io.decodersBefore(i).destinationTag
    sourceTagSelector2.io.beforeDestinationTag(i).valid := io.decodersBefore(i).destinationRegister === instRs2
  }
  val sourceTag2 = sourceTagSelector2.io.sourceTag

  // Valueの選択
  // value1
  val valueSelector1 = Module(new ValueSelector1)
  valueSelector1.io.value.ready := true.B
  valueSelector1.io.sourceTag <> sourceTag1
  valueSelector1.io.reorderBufferValue <> io.reorderBuffer.source1.value
  valueSelector1.io.registerFileValue := io.registerFile.value1
  for (i <- 0 until params.runParallel) {
    valueSelector1.io.executorBypassValue(i) <> io.executors(i)
  }
  // value2
  val valueSelector2 = Module(new ValueSelector2)
  valueSelector2.io.value.ready := true.B
  valueSelector2.io.sourceTag <> sourceTag2
  valueSelector2.io.reorderBufferValue <> io.reorderBuffer.source2.value
  valueSelector2.io.registerFileValue := io.registerFile.value2
  valueSelector2.io.immediateValue := MuxLookup(opcodeFormatChecker.io.format.asUInt, 0.S, Seq(
    I.asUInt -> immIExtended,
    U.asUInt -> immUExtended,
    J.asUInt -> immJExtended
  ))
  valueSelector2.io.opcodeFormat := opcodeFormatChecker.io.format
  for (i <- 0 until params.runParallel) {
    valueSelector2.io.executorBypassValue(i) <> io.executors(i)
  }

  // 前のデコーダから次のデコーダへ
  for (i <- 0 until instructionOffset) {
    io.decodersAfter(i) <> io.decodersBefore(i)
  }
  // 次のデコーダへ伝える情報
  when(destinationIsValid && instRd =/= 0.U) {
    io.decodersAfter(instructionOffset).destinationTag := io.reorderBuffer.destination.destinationTag
    io.decodersAfter(instructionOffset).destinationRegister := instRd
    io.decodersAfter(instructionOffset).valid := true.B
  } otherwise {
    io.decodersAfter(instructionOffset).destinationTag := 0.U
    io.decodersAfter(instructionOffset).destinationRegister := 0.U
    io.decodersAfter(instructionOffset).valid := false.B
  }

  // リオーダバッファからの情報は常に受け取ることにする
  io.reorderBuffer.source1.matchingTag.ready := true.B
  io.reorderBuffer.source1.value.ready := true.B
  io.reorderBuffer.source2.matchingTag.ready := true.B
  io.reorderBuffer.source2.value.ready := true.B

  // 命令をデコードするのはリオーダバッファにエントリの空きがあり、リザベーションステーションにも空きがあるとき
  io.instructionFetch.ready := io.reservationStation.ready && io.reorderBuffer.ready
  // リオーダバッファやリザベーションステーションに新しいエントリを追加するのは命令がある時
  io.reorderBuffer.valid := io.instructionFetch.valid
  io.reservationStation.entry.valid := io.instructionFetch.valid

  // RSへの出力を埋める
  val rs = io.reservationStation.entry
  rs.opcode := instOp
  rs.function3 := instFunct3
  rs.immediateOrFunction7 := MuxLookup(opcodeFormatChecker.io.format.asUInt, 0.U, Seq(
    R.asUInt -> Cat("b000000".U, instFunct7),
    S.asUInt -> instImmS,
    B.asUInt -> instImmB,
  ))
  rs.destinationTag := io.reorderBuffer.destination.destinationTag
  rs.sourceTag1 := Mux(valueSelector1.io.value.valid, 0.U, sourceTag1.tag)
  rs.sourceTag2 := Mux(valueSelector2.io.value.valid, 0.U, sourceTag2.tag)
  rs.ready1 := valueSelector1.io.value.valid
  rs.ready2 := valueSelector2.io.value.valid
  rs.value1 := valueSelector1.io.value.bits
  rs.value2 := valueSelector2.io.value.bits
  rs.programCounter := io.instructionFetch.bits.programCounter

  // load or store命令の場合，LSQへ発送
  io.loadStoreQueue.valid := false.B
  io.loadStoreQueue.stag2 <> sourceTagSelector2.io.sourceTag
  io.loadStoreQueue.value <> valueSelector2.io.value
  io.loadStoreQueue.opcode := instOp
  io.loadStoreQueue.programCounter := io.instructionFetch.bits.programCounter
  when(io.loadStoreQueue.ready && io.loadStoreQueue.opcode === BitPat("b0?00011")) {
    io.loadStoreQueue.valid := true.B
  }
}

object Decoder extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new Decoder(0))
}