package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{ExecutorBranchResult, Fetch2BranchPrediction, Fetch2Decoder, InstructionCache2Fetch}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

/** 命令フェッチ用モジュール */
class Fetch(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    /** 命令キャッシュ */
    val cache = Flipped(Vec(params.numberOfDecoders, new InstructionCache2Fetch))
    /** 分岐予測 */
    val prediction = Vec(params.numberOfDecoders, new Fetch2BranchPrediction)
    /** リオーダバッファの中身が空である */
    val reorderBufferEmpty = Input(Bool())
    /** ロードストアキューが空である */
    val loadStoreQueueEmpty = Input(Bool())
    /** 実行ユニットから分岐先の計算結果が帰ってきた */
    val executorBranchResult = Vec(params.numberOfALUs, Input(new ExecutorBranchResult))

    /** デコーダ */
    val decoders = Vec(params.numberOfDecoders, new Fetch2Decoder)

    /** デバッグ用 */
    val PC = if (params.debug) Some(Output(SInt(64.W))) else None
    val nextPC = if (params.debug) Some(Output(SInt(64.W))) else None
    val branchTypes = if (params.debug) Some(Output(Vec(params.numberOfDecoders, new BranchType.Type))) else None
  })

  /** プログラムカウンタ */
  val pc = RegInit(params.pcInit.S(64.W))
  /** フェッチの停止と理由 */
  val waiting = RegInit(Waiting.notWaiting())

  var nextPC = pc
  var nextWait = waiting
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    val cache = io.cache(i)

    cache.address := nextPC
    decoder.valid := io.cache(i).output.valid && !nextWait.isWaiting
    decoder.bits.programCounter := nextPC
    decoder.bits.instruction := cache.output.bits

    val branch = Module(new CheckBranch)
    branch.io.instruction := io.cache(i).output.bits
    if (params.debug)
      io.branchTypes.get(i) := branch.io.branchType

    // 次に停止する必要があるか確認
    nextWait = Mux(nextWait.isWaiting, nextWait, MuxLookup(branch.io.branchType.asUInt, nextWait, Seq(
      BranchType.Branch.asUInt -> Waiting.waitFor(BranchType.Branch),
      BranchType.JALR.asUInt -> Waiting.waitFor(BranchType.JALR),
      BranchType.Fence.asUInt -> Waiting.waitFor(BranchType.Fence),
      BranchType.FenceI.asUInt -> Waiting.waitFor(BranchType.FenceI),
    )))
    // PCの更新を確認
    nextPC = nextPC + Mux(nextWait.isWaiting,
      0.S,
      MuxLookup(branch.io.branchType.asUInt, 4.S, Seq(
        BranchType.JAL.asUInt -> branch.io.offset
      )))
  }
  pc := nextPC
  waiting := nextWait

  // 停止している際の挙動
  when(waiting.isWaiting) {
    when(waiting.reason === BranchType.Branch || waiting.reason === BranchType.JALR) {
      for (e <- io.executorBranchResult) {
        when(e.valid) {
          waiting := Waiting.notWaiting()
          pc := e.branchAddress
        }
      }
    }
    when(waiting.reason === BranchType.Fence || waiting.reason === BranchType.FenceI) {
      when(io.reorderBufferEmpty && io.loadStoreQueueEmpty) {
        waiting := Waiting.notWaiting()
        pc := pc + 4.S
      }
    }
  }

  if (params.debug) {
    io.PC.get := pc
    io.nextPC.get := nextPC
  }

  // TODO: 分岐予測を使う
  for (p <- io.prediction) {
    p.isBranch := DontCare
    p.prediction := DontCare
    p.addressLowerBits := DontCare
  }
}

object Fetch extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new Fetch(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}