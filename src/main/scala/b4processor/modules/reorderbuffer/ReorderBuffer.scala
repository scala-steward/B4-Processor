package b4processor.modules.reorderbuffer

import b4processor.Parameters
import b4processor.connections.{
  BranchPrediction2ReorderBuffer,
  CollectedOutput,
  Decoder2ReorderBuffer,
  LoadStoreQueue2ReorderBuffer,
  ReorderBuffer2CSR,
  ReorderBuffer2RegisterFile,
  ResultType
}
import b4processor.utils.Tag
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

/** リオーダバッファ
  *
  * @param params
  *   パラメータ
  */
class ReorderBuffer(threadId: Int)(implicit params: Parameters) extends Module {
  private val tagWidth = params.tagWidth

  val io = IO(new Bundle {
    val decoders =
      Vec(params.decoderPerThread, Flipped(new Decoder2ReorderBuffer))
    val collectedOutputs = Flipped(new CollectedOutput)
    val registerFile =
      Vec(
        params.maxRegisterFileCommitCount,
        Valid(new ReorderBuffer2RegisterFile())
      )
    val loadStoreQueue = Vec(
      params.maxRegisterFileCommitCount,
      Valid(new LoadStoreQueue2ReorderBuffer)
    )
    val isEmpty = Output(Bool())
    val csr = new ReorderBuffer2CSR

    val head = if (params.debug) Some(Output(UInt(tagWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(tagWidth.W))) else None
    val bufferIndex0 =
      if (params.debug) Some(Output(new ReorderBufferEntry)) else None
  })

  val head = RegInit(0.U(tagWidth.W))
  val tail = RegInit(0.U(tagWidth.W))
  val buffer = RegInit(
    VecInit(Seq.fill(math.pow(2, tagWidth).toInt)(ReorderBufferEntry.default))
  )

  class RegisterTagMapContent extends Bundle {
    val valid = Bool()
    val tagId = UInt(tagWidth.W)
  }

  val registerTagMap = RegInit(
    VecInit(
      Seq.fill(32)(
        new RegisterTagMapContent().Lit(_.valid -> false.B, _.tagId -> 0.U)
      )
    )
  )

  // レジスタファイルへの書き込み
  var lastValid = true.B
  for (((rf, lsq), i) <- io.registerFile.zip(io.loadStoreQueue).zipWithIndex) {
    val index = tail + i.U

    val instructionOk = buffer(index).valueReady || buffer(index).storeSign
    val canCommit = lastValid && index =/= head && instructionOk

    rf.valid := canCommit
    when(canCommit) {
      rf.bits.value := buffer(index).value
      rf.bits.destinationRegister := buffer(index).destinationRegister
      when(index === registerTagMap(buffer(index).destinationRegister).tagId) {
        registerTagMap(
          buffer(index).destinationRegister
        ) := new RegisterTagMapContent().Lit(_.valid -> false.B)
      }
    }.otherwise {
      rf.bits.value := 0.U
      rf.bits.destinationRegister := 0.U
    }

    when(canCommit) {
      // LSQへストア実行信号
      lsq.bits.destinationTag := Tag.fromWires(threadId.U, index)
      lsq.valid := buffer(index).storeSign
      buffer(index) := ReorderBufferEntry.default
    }.otherwise {
      lsq.bits.destinationTag := Tag(threadId, 0)
      lsq.valid := false.B
    }
    lastValid = canCommit
  }
  val tailDelta = MuxCase(
    params.maxRegisterFileCommitCount.U,
    io.registerFile.zipWithIndex.map { case (entry, index) =>
      !entry.valid -> index.U
    }
  )
  tail := tail + tailDelta
  io.csr.retireCount := tailDelta

  // デコーダからの読み取りと書き込み
  var insertIndex = head
  private var lastReady = true.B
  for (i <- 0 until params.decoderPerThread) {
    val decoder = io.decoders(i)
    decoder.ready := lastReady && (insertIndex + 1.U) =/= tail
    when(decoder.valid && decoder.ready) {
      //      if (params.debug)
      //        printf(p"reorder buffer new entry pc = ${decoder.programCounter} destinationRegister=${decoder.destination.destinationRegister} in ${insertIndex} prediction=${decoder.isPrediction}\n")
      buffer(insertIndex) := {
        val entry = Wire(new ReorderBufferEntry)
        entry.value := 0.U
        entry.valueReady := false.B
        entry.destinationRegister := decoder.destination.destinationRegister
        entry.storeSign := decoder.destination.storeSign
        entry.programCounter := decoder.programCounter
        entry
      }
    }
    decoder.destination.destinationTag := Tag.fromWires(threadId.U, insertIndex)
    when(decoder.destination.destinationRegister =/= 0.U) {
      registerTagMap(decoder.destination.destinationRegister).valid := true.B
      registerTagMap(
        decoder.destination.destinationRegister
      ).tagId := insertIndex
    }
    when(decoder.source1.sourceRegister =/= 0.U) {
      val matchingBits = registerTagMap(decoder.source1.sourceRegister)
      val hasMatching = matchingBits.valid

      decoder.source1.matchingTag.valid := hasMatching
      decoder.source1.matchingTag.bits := Tag.fromWires(
        threadId.U,
        matchingBits.tagId
      )
      decoder.source1.value.valid := buffer(matchingBits.tagId).valueReady
      decoder.source1.value.bits := buffer(matchingBits.tagId).value
    }.otherwise {
      decoder.source1.matchingTag.valid := false.B
      decoder.source1.matchingTag.bits := Tag(threadId, 0)
      decoder.source1.value.valid := false.B
      decoder.source1.value.bits := 0.U
    }

    when(decoder.source2.sourceRegister =/= 0.U) {
      val matchingBits = registerTagMap(decoder.source2.sourceRegister)
      val hasMatching = matchingBits.valid
      decoder.source2.matchingTag.valid := hasMatching
      decoder.source2.matchingTag.bits := Tag.fromWires(
        threadId.U,
        matchingBits.tagId
      )
      decoder.source2.value.valid := buffer(matchingBits.tagId).valueReady
      decoder.source2.value.bits := buffer(matchingBits.tagId).value
    }.otherwise {
      decoder.source2.matchingTag.valid := false.B
      decoder.source2.matchingTag.bits := Tag(threadId, 0)
      decoder.source2.value.valid := false.B
      decoder.source2.value.bits := 0.U
    }

    // 次のループで使用するinserIndexとlastReadyを変える
    // わざと:=ではなく=を利用している
    insertIndex =
      Mux(decoder.valid && decoder.ready, insertIndex + 1.U, insertIndex)
    lastReady = decoder.ready
  }
  head := insertIndex

  io.isEmpty := head === tail

  // 出力の読み込み
  private val output = io.collectedOutputs.outputs
  when(
    output.valid && output.bits.resultType === ResultType.Result && output.bits.tag.threadId === threadId.U
  ) {
    buffer(output.bits.tag.id).value := output.bits.value
    buffer(output.bits.tag.id).valueReady := true.B
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
    Parameters(decoderPerThread = 2, maxRegisterFileCommitCount = 8)
  (new ChiselStage).emitVerilog(
    new ReorderBuffer(0),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
