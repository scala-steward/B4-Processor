package b4processor.modules.reorderbuffer

import b4processor.Parameters
import b4processor.connections.{
  BranchBuffer2ReorderBuffer,
  CollectedOutput,
  Decoder2ReorderBuffer,
  LoadStoreQueue2ReorderBuffer,
  ReorderBuffer2RegisterFile
}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import PredictionStatus._

import scala.math.pow

/** リオーダバッファ
  *
  * @param params
  *   パラメータ
  */
class ReorderBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.runParallel, Flipped(new Decoder2ReorderBuffer))
    val collectedOutputs = Flipped(new CollectedOutput)
    val registerFile =
      Vec(
        params.maxRegisterFileCommitCount,
        Valid(new ReorderBuffer2RegisterFile)
      )
    val loadStoreQueue = Output(new LoadStoreQueue2ReorderBuffer)
    val branchBuffer = Flipped(new BranchBuffer2ReorderBuffer)
    val flushOutput = Output(Bool())
    val isEmpty = Output(Bool())

    val head = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val bufferIndex0 =
      if (params.debug) Some(Output(new ReorderBufferEntry)) else None
  })

  io.flushOutput := false.B

  private val head = RegInit(0.U(params.tagWidth.W))
  private val tail = RegInit(0.U(params.tagWidth.W))
  private val buffer = RegInit(
    VecInit(
      Seq.fill(math.pow(2, params.tagWidth).toInt)(ReorderBufferEntry.default)
    )
  )

  private val flush = RegInit(false.B)
  private val flushPending = RegInit(false.B)
  private val flushUntil = Reg(UInt(params.tagWidth.W))

  // デコーダからの読み取りと書き込み
  private var nextHead = head
  private var lastReady = true.B
  for (i <- 0 until params.runParallel) {
    val decoder = io.decoders(i)
    decoder.ready := lastReady && (nextHead + 1.U) =/= tail && !flushPending
    when(decoder.valid && decoder.ready) {
      //      if (params.debug)
      //        printf(p"reorder buffer new entry pc = ${decoder.programCounter} destinationRegister=${decoder.destination.destinationRegister} in ${insertIndex} prediction=${decoder.isPrediction}\n")
      buffer(nextHead) := {
        val entry = Wire(new ReorderBufferEntry)
        entry.value := 0.U
        entry.valueReady := false.B
        entry.programCounter := decoder.programCounter
        entry.destinationRegister := decoder.destination.destinationRegister
        entry.storeSign := decoder.destination.storeSign
        entry.prediction := Mux(decoder.isBranch, Predicted, NotBranch)
        entry.branchID := decoder.branchID
        entry
      }
    }
    decoder.destination.destinationTag := nextHead
    // ソースレジスタに対応するタグ、値の代入
    val descendingIndex = VecInit(
      (0 until pow(2, params.tagWidth).toInt).map(indexOffset =>
        head - indexOffset.U
      )
    )
    when(decoder.source1.sourceRegister =/= 0.U) {
      val matchingBits = Cat(
        buffer.reverse.map(entry =>
          entry.destinationRegister === decoder.source1.sourceRegister
        )
      )
        .suggestName(s"matchingBits_d${i}_s1")
      val hasMatching = matchingBits.orR
        .suggestName(s"hasMatching_d${i}_s1")
      val matchingIndex = MuxCase(
        0.U,
        descendingIndex.map { index => matchingBits(index).asBool -> index }
      )
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
      val matchingBits = Cat(
        buffer.reverse.map(entry =>
          entry.destinationRegister === decoder.source2.sourceRegister
        )
      )
        .suggestName(s"matchingBits_d${i}_s2")
      val hasMatching = matchingBits.orR
      val matchingIndex = MuxCase(
        0.U,
        descendingIndex.map { index => matchingBits(index).asBool -> index }
      )
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
    nextHead = nextHead + Mux(decoder.valid && decoder.ready, 1.U, 0.U)
    lastReady = decoder.ready
  }
  head := nextHead

  // レジスタファイルへの書き込み
  {
    var lastValid = true.B
    var nextTail = tail
    for (i <- 0 until params.maxRegisterFileCommitCount) {
      val index = tail + i.U
      val rf = io.registerFile(i)

      val entry = buffer(index)

      val instructionOk = entry.valueReady || entry.storeSign
      val canCommit =
        lastValid && index =/= head && instructionOk && (entry.prediction === NotBranch || entry.prediction === Correct) && !flush

      rf.valid := canCommit
      when(rf.valid) {
        rf.bits.value := entry.value
        rf.bits.destinationRegister := entry.destinationRegister
      }.otherwise {
        rf.bits.value := 0.U
        rf.bits.destinationRegister := 0.U
      }

      when(canCommit) {
        // LSQへストア実行信号
        io.loadStoreQueue.destinationTag(i) := index
        io.loadStoreQueue.valid(i) := entry.storeSign
        entry := ReorderBufferEntry.default
      }.otherwise {
        io.loadStoreQueue.destinationTag(i) := 0.U
        io.loadStoreQueue.valid(i) := false.B
      }
      nextTail = Mux(canCommit, nextTail + 1.U, nextTail)

      lastValid = canCommit
    }
    when(buffer(tail).prediction === Incorrect && flushPending && !flush) {
      flush := true.B
      io.flushOutput := true.B
      io.registerFile(0).valid := true.B
      io.registerFile(0).bits.value := buffer(tail).value
      io.registerFile(0).bits.destinationRegister :=
        buffer(tail).destinationRegister
    }
    when(flush) {
      flush := false.B
      flushPending := false.B
      // すべてのエントリを無効にする
      for (b <- buffer)
        b.destinationRegister := 0.U
      tail := flushUntil
    }.otherwise {
      tail := nextTail
    }
  }

  io.isEmpty := head === tail

  // 出力の読み込み
  when(!flush) {
    for (output <- io.collectedOutputs.outputs) {
      when(output.validAsResult) {
        buffer(output.tag).value := output.value
        buffer(output.tag).valueReady := true.B
      }
    }
  }

  // 分岐の確認
  when(io.branchBuffer.valid && !flushPending && !flush) {
    for (b <- buffer) {
      when(
        b.branchID === io.branchBuffer.bits.BranchID && b.prediction === Predicted
      ) {
        when(io.branchBuffer.bits.correct) {
          b.prediction := Correct
        }.otherwise {
          b.prediction := Incorrect
        }
      }
    }
    when(!io.branchBuffer.bits.correct) {
      flushPending := true.B
      flushUntil := nextHead
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
  implicit val params =
    Parameters().copy(
      runParallel = 2,
      maxRegisterFileCommitCount = 8,
      tagWidth = 5
    )
  (new ChiselStage).emitVerilog(
    new ReorderBuffer,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
