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
class ReorderBuffer(implicit params: Parameters) extends Module {
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

    val isError = Output(Bool())
    val threadId = Input(UInt(log2Up(params.threads).W))

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

  private object RegisterTagMapContent {
    def default: RegisterTagMapContent =
      new RegisterTagMapContent().Lit(_.valid -> false.B, _.tagId -> 0.U)
    def apply(tagId: UInt): RegisterTagMapContent = {
      val w = Wire(new RegisterTagMapContent)
      w.valid := true.B
      w.tagId := tagId
      w
    }
  }

  private val registerTagMap = RegInit(
    VecInit(Seq.fill(32)(RegisterTagMapContent.default))
  )

  class DecoderMap extends Bundle {
    val valid = Bool()
    val tagId = UInt(tagWidth.W)
    val destinationRegister = UInt(5.W)
  }

  private object DecoderMap {
    def default: DecoderMap =
      new DecoderMap().Lit(
        _.valid -> false.B,
        _.tagId -> 0.U,
        _.destinationRegister -> 0.U
      )
  }

  private val previousDecoderMap =
    Seq.fill(params.decoderPerThread - 1)(WireInit(DecoderMap.default))

  // レジスタファイルへの書き込み
  io.isError := false.B
  private var lastValid = true.B
  for (((rf, lsq), i) <- io.registerFile.zip(io.loadStoreQueue).zipWithIndex) {
    val index = tail + i.U

    val instructionOk = buffer(index).valueReady || buffer(index).storeSign
    val canCommit = lastValid && index =/= head && instructionOk
    val isError = buffer(index).isError

    rf.valid := canCommit && !isError
    rf.bits.value := 0.U
    rf.bits.destinationRegister := 0.U
    io.csr.mcause.valid := false.B
    io.csr.mcause.bits := DontCare
    when(canCommit) {
      when(!isError) {
        rf.bits.value := buffer(index).value
        rf.bits.destinationRegister := buffer(index).destinationRegister
        when(
          index === registerTagMap(buffer(index).destinationRegister).tagId
        ) {
          registerTagMap(
            buffer(index).destinationRegister
          ) := new RegisterTagMapContent().Lit(_.valid -> false.B)
        }
      }.otherwise {
        io.csr.mcause.valid := true.B
        io.csr.mcause.bits := buffer(index).value
        io.isError := true.B
      }
    }

    when(canCommit) {
      // LSQへストア実行信号
      lsq.bits.destinationTag := Tag.fromWires(io.threadId, index)
      lsq.valid := buffer(index).storeSign
      buffer(index) := ReorderBufferEntry.default
    }.otherwise {
      lsq.bits.destinationTag := Tag.fromWires(io.threadId, 0.U)
      lsq.valid := false.B
    }
    lastValid = canCommit
  }
  private val tailDelta = MuxCase(
    params.maxRegisterFileCommitCount.U,
    io.registerFile.zipWithIndex.map { case (entry, index) =>
      !entry.valid -> index.U
    }
  )

  // デコーダからの読み取りと書き込み
  private var insertIndex = head
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
        entry.isError := false.B
        entry
      }
    }
    if (i < params.decoderPerThread - 1) {
      previousDecoderMap(i).valid :=
        decoder.valid && decoder.destination.destinationRegister =/= 0.U
      previousDecoderMap(i).tagId := insertIndex
      previousDecoderMap(i).destinationRegister :=
        decoder.destination.destinationRegister
    }
    decoder.destination.destinationTag := Tag.fromWires(
      io.threadId,
      insertIndex
    )
    when(decoder.destination.destinationRegister =/= 0.U) {
      registerTagMap(decoder.destination.destinationRegister).valid := true.B
      registerTagMap(decoder.destination.destinationRegister).tagId :=
        insertIndex
    }
    when(decoder.source1.sourceRegister =/= 0.U) {
      val matchingBits = MuxCase(
        registerTagMap(decoder.source1.sourceRegister),
        (0 until i)
          .map(n =>
            (previousDecoderMap(n).valid &&
              previousDecoderMap(n).destinationRegister
              === decoder.source1.sourceRegister) ->
              RegisterTagMapContent(previousDecoderMap(n).tagId)
          )
          .reverse
      )
      val hasMatching = matchingBits.valid

      decoder.source1.matchingTag.valid := hasMatching
      decoder.source1.matchingTag.bits := Tag.fromWires(
        io.threadId,
        matchingBits.tagId
      )
      decoder.source1.value.valid := buffer(matchingBits.tagId).valueReady
      decoder.source1.value.bits := buffer(matchingBits.tagId).value
    }.otherwise {
      decoder.source1.matchingTag.valid := false.B
      decoder.source1.matchingTag.bits := Tag.fromWires(io.threadId, 0.U)
      decoder.source1.value.valid := false.B
      decoder.source1.value.bits := 0.U
    }

    when(decoder.source2.sourceRegister =/= 0.U) {
      val matchingBits = MuxCase(
        registerTagMap(decoder.source2.sourceRegister),
        (0 until i)
          .map(n =>
            (previousDecoderMap(n).valid &&
              previousDecoderMap(n).destinationRegister
              === decoder.source2.sourceRegister) ->
              RegisterTagMapContent(previousDecoderMap(n).tagId)
          )
          .reverse
      )
      val hasMatching = matchingBits.valid
      decoder.source2.matchingTag.valid := hasMatching
      decoder.source2.matchingTag.bits := Tag.fromWires(
        io.threadId,
        matchingBits.tagId
      )
      decoder.source2.value.valid := buffer(matchingBits.tagId).valueReady
      decoder.source2.value.bits := buffer(matchingBits.tagId).value
    }.otherwise {
      decoder.source2.matchingTag.valid := false.B
      decoder.source2.matchingTag.bits := Tag.fromWires(io.threadId, 0.U)
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
  tail := Mux(io.isError, insertIndex, tail + tailDelta)
  io.csr.retireCount := Mux(io.isError, 0.U, tailDelta)
  io.isEmpty := head === tail

  // 出力の読み込み
  private val output = io.collectedOutputs.outputs
  when(
    output.valid && (output.bits.resultType === ResultType.Result) && (output.bits.tag.threadId === io.threadId)
  ) {
    buffer(output.bits.tag.id).value := output.bits.value
    buffer(output.bits.tag.id).isError := output.bits.isError
    buffer(output.bits.tag.id).valueReady := true.B
  }

  registerTagMap(0).valid := false.B
  registerTagMap(0).tagId := 0.U

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
    Parameters(decoderPerThread = 2, maxRegisterFileCommitCount = 1)
  (new ChiselStage).emitVerilog(
    new ReorderBuffer,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
