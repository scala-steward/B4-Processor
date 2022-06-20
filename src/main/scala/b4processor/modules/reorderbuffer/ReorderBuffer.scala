package b4processor.modules.reorderbuffer

import b4processor.Parameters
import b4processor.connections.{BranchPrediction2ReorderBuffer, CollectedOutput, Decoder2ReorderBuffer, LoadStoreQueue2ReorderBuffer, ReorderBuffer2RegisterFile}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

/**
 * リオーダバッファ
 *
 * @param params パラメータ
 */
class ReorderBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.runParallel, Flipped(new Decoder2ReorderBuffer))
    val collectedOutputs = Flipped(new CollectedOutput)
    val registerFile = Vec(params.maxRegisterFileCommitCount, new ReorderBuffer2RegisterFile())
    val loadStoreQueue = Output(new LoadStoreQueue2ReorderBuffer)
    val isEmpty = Output(Bool())

    val head = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val bufferIndex0 = if (params.debug) Some(Output(new ReorderBufferEntry)) else None
  })

  val head = RegInit(0.U(params.tagWidth.W))
  val tail = RegInit(0.U(params.tagWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt)(ReorderBufferEntry.default)))

  // デコーダからの読み取りと書き込み
  var insertIndex = head
  var lastReady = true.B
  for (i <- 0 until params.runParallel) {
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
        entry.storeSign := decoder.destination.storeSign
        entry
      }
    }
    decoder.destination.destinationTag := insertIndex
    // ソースレジスタに対応するタグ、値の代入
    val descendingIndex = VecInit((0 until pow(2, params.tagWidth).toInt).map(indexOffset => head - indexOffset.U))
    when(decoder.source1.sourceRegister =/= 0.U) {
      val matchingBits = Cat(buffer.reverse.map(entry => entry.destinationRegister === decoder.source1.sourceRegister))
        .suggestName(s"matchingBits_d${i}_s1")
      val hasMatching = matchingBits.orR
        .suggestName(s"hasMatching_d${i}_s1")
      val matchingIndex = MuxCase(0.U, descendingIndex.map { index => matchingBits(index).asBool -> index })
        .suggestName(s"matchingIndex_d${i}_s1")
      decoder.source1.matchingTag.valid := hasMatching
      decoder.source1.matchingTag.bits := matchingIndex
      decoder.source1.value.valid := buffer(matchingIndex).valueReady
      decoder.source1.value.bits := buffer(matchingIndex).value
    }.otherwise {
      decoder.source1.matchingTag.valid := false.B
      decoder.source1.matchingTag.bits := 0.U
      decoder.source1.value.valid := false.B
      decoder.source1.value.bits := 0.U
    }

    when(decoder.source2.sourceRegister =/= 0.U) {
      val matchingBits = Cat(buffer.reverse.map(entry => entry.destinationRegister === decoder.source2.sourceRegister))
        .suggestName(s"matchingBits_d${i}_s2")
      val hasMatching = matchingBits.orR
      val matchingIndex = MuxCase(0.U, descendingIndex.map { index => matchingBits(index).asBool -> index })
      decoder.source2.matchingTag.valid := hasMatching
      decoder.source2.matchingTag.bits := matchingIndex
      decoder.source2.value.valid := buffer(matchingIndex).valueReady
      decoder.source2.value.bits := buffer(matchingIndex).value
    }.otherwise {
      decoder.source2.matchingTag.valid := false.B
      decoder.source2.matchingTag.bits := 0.U
      decoder.source2.value.valid := false.B
      decoder.source2.value.bits := 0.U
    }


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
    val canCommit = lastValid && index =/= head && instructionOk

    io.registerFile(i).valid := canCommit
    when(canCommit) {
      io.registerFile(i).bits.value := buffer(index).value
      io.registerFile(i).bits.destinationRegister := buffer(index).destinationRegister
    }.otherwise {
      io.registerFile(i).bits.value := 0.U
      io.registerFile(i).bits.destinationRegister := 0.U
    }

    when(canCommit) {
      // LSQへストア実行信号
      io.loadStoreQueue.destinationTag(i) := index
      io.loadStoreQueue.valid(i) := buffer(index).storeSign

    when(canCommit) {
      buffer(index) := ReorderBufferEntry.default
    }
    lastValid = canCommit
  }
  tail := tail + MuxCase(params.maxRegisterFileCommitCount.U, io.registerFile.zipWithIndex.map { case (entry, index) => !entry.valid -> index.U })

  io.isEmpty := head === tail

  // 出力の読み込み
  for (output <- io.collectedOutputs.outputs) {
    when(output.validAsResult) {
      buffer(output.tag).value := output.value
      buffer(output.tag).valueReady := true.B
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
  implicit val params = Parameters(runParallel = 2, maxRegisterFileCommitCount = 8, tagWidth = 5)
  (new ChiselStage).emitVerilog(new ReorderBuffer, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}