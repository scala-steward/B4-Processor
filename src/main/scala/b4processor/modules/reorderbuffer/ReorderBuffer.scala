package b4processor.modules.reorderbuffer

import b4processor.Parameters
import b4processor.connections.{BranchPrediction2ReorderBuffer, Decoder2ReorderBuffer, ExecutionRegisterBypass, ReorderBuffer2RegisterFile}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

/**
 * リオーダバッファ
 *
 * @param params パラメータ
 */
class ReorderBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.numberOfDecoders, Flipped(new Decoder2ReorderBuffer))
    val alus = Vec(params.numberOfALUs, Flipped(new ExecutionRegisterBypass))
    val prediction = Flipped(new BranchPrediction2ReorderBuffer())
    val registerFile = Vec(params.maxRegisterFileCommitCount, new ReorderBuffer2RegisterFile())

    val head = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val bufferIndex0 = if (params.debug) Some(Output(new ReorderBufferEntry)) else None
  })

  val defaultEntry = {
    val entry = Wire(new ReorderBufferEntry)
    entry.value := 0.U
    entry.valueReady := false.B
    entry.programCounter := 0.U
    entry.destinationRegister := 0.U
    entry.isPrediction := false.B
    entry.commitReady := false.B
    entry
  }

  val head = RegInit(0.U(params.tagWidth.W))
  val tail = RegInit(0.U(params.tagWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt)(defaultEntry)))

  // デコーダからの読み取りと書き込み
  var insertIndex = head
  var lastReady = true.B
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    decoder.ready := lastReady && (insertIndex + 1.U) =/= tail
    when(decoder.valid && decoder.ready) {
      //      if (params.debug)
      //        printf(p"reorder buffer new entry pc = ${decoder.programCounter} destinationRegister=${decoder.destination.destinationRegister} in ${insertIndex} prediction=${decoder.isPrediction}\n")
      buffer(insertIndex) := {
        val entry = Wire(new ReorderBufferEntry)
        entry.value := 0.U
        entry.valueReady := false.B
        entry.programCounter := decoder.programCounter
        entry.destinationRegister := decoder.destination.destinationRegister
        entry.isPrediction := decoder.isPrediction
        entry.commitReady := false.B
        entry
      }
    }
    decoder.destination.destinationTag := insertIndex
    // ソースレジスタに対応するタグ、値の代入
    decoder.source1.matchingTag.valid := buffer.map { entry => entry.destinationRegister === decoder.source1.sourceRegister }.fold(false.B) { (a, b) => a | b }
    decoder.source1.matchingTag.bits := MuxCase(0.U, buffer.zipWithIndex.map { case (entry, index) => (entry.destinationRegister === decoder.source1.sourceRegister) -> index.U })
    decoder.source1.value.valid := buffer.map { entry => entry.destinationRegister === decoder.source1.sourceRegister && entry.valueReady }.fold(false.B) { (a, b) => a | b }
    decoder.source1.value.bits := MuxCase(0.U, buffer.map { entry => (entry.destinationRegister === decoder.source1.sourceRegister && entry.valueReady) -> entry.value })
    decoder.source2.matchingTag.valid := buffer.map { entry => entry.destinationRegister === decoder.source2.sourceRegister }.fold(false.B) { (a, b) => a | b }
    decoder.source2.matchingTag.bits := MuxCase(0.U, buffer.zipWithIndex.map { case (entry, index) => (entry.destinationRegister === decoder.source2.sourceRegister) -> index.U })
    decoder.source2.value.valid := buffer.map { entry => entry.destinationRegister === decoder.source2.sourceRegister && entry.valueReady }.fold(false.B) { (a, b) => a | b }
    decoder.source2.value.bits := MuxCase(0.U, buffer.map { entry => (entry.destinationRegister === decoder.source2.sourceRegister && entry.valueReady) -> entry.value })

    // 次のループで使用するinserIndexとlastReadyを変える
    // わざと:=ではなく=を利用している
    insertIndex = insertIndex + Mux(decoder.valid && decoder.ready, 1.U, 0.U)
    lastReady = decoder.ready
  }
  head := insertIndex

  // レジスタファイルへの書き込み
  var lastValid = true.B
  for (i <- 0 until params.maxRegisterFileCommitCount) {
    val index = tail + i.U

    val instructionOk = buffer(index).valueReady
    val predictionOK = !buffer(index).isPrediction || buffer(index).commitReady
    val canCommit = lastValid && index =/= head && instructionOk && predictionOK

    val skip = buffer(index).isPrediction && buffer(index).commitReady

    when(skip) {
      io.registerFile(i).valid := false.B
      io.registerFile(i).bits.value := 0.U
      io.registerFile(i).bits.destinationRegister := 0.U
    }.otherwise {
      io.registerFile(i).valid := canCommit
      io.registerFile(i).bits.value := buffer(index).value
      io.registerFile(i).bits.destinationRegister := buffer(index).destinationRegister
    }

    when(canCommit) {
      buffer(index) := defaultEntry
    }
    lastValid = canCommit
  }
  tail := tail + MuxCase(params.maxRegisterFileCommitCount.U, io.registerFile.zipWithIndex.map { case (entry, index) => !entry.valid -> index.U })

  // ALUの読み込み
  for (alu <- io.alus) {
    alu.ready := true.B
    when(alu.valid) {
      buffer(alu.bits.destinationTag).value := alu.bits.value
      buffer(alu.bits.destinationTag).valueReady := true.B
    }
  }

  // 分岐予測の読み込み
  when(io.prediction.valid) {
    for (buf <- buffer) {
      when(buf.isPrediction) {
        buf.isPrediction := !io.prediction.wasCorrect
        buf.commitReady := true.B
      }
    }
  }

  // デバッグ
  if (params.debug) {
    io.head.get := head
    io.tail.get := tail
    io.bufferIndex0.get := buffer(0)
    //    printf(p"reorder buffer pc=${buffer(0).programCounter} value=${buffer(0).value} ready=${buffer(0).ready} rd=${buffer(0).destinationRegister}\n")
  }
}

object ReorderBuffer extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new ReorderBuffer, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}