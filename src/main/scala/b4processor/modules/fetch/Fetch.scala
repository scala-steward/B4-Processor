package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{
  Fetch2BranchPrediction,
  Fetch2FetchBuffer,
  InstructionCache2Fetch
}
import b4processor.modules.branch_output_collector.CollectedBranchAddresses
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

/** 命令フェッチ用モジュール */
class Fetch(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {

    /** 命令キャッシュ */
    val cache = Flipped(Vec(params.runParallel, new InstructionCache2Fetch))

    /** 分岐予測 */
    val prediction = Vec(params.runParallel, new Fetch2BranchPrediction)

    /** リオーダバッファの中身が空である */
    val reorderBufferEmpty = Input(Bool())

    /** ロードストアキューが空である */
    val loadStoreQueueEmpty = Input(Bool())

    /** 実行ユニットから分岐先の計算結果が帰ってきた */
    val collectedBranchAddresses = Flipped(new CollectedBranchAddresses)

    /** デコーダ */
    val fetchBuffer = new Fetch2FetchBuffer

    /** デバッグ用 */
    val PC = if (params.debug) Some(Output(UInt(64.W))) else None
    val nextPC = if (params.debug) Some(Output(UInt(64.W))) else None
    val branchTypes =
      if (params.debug)
        Some(Output(Vec(params.runParallel, new BranchType.Type)))
      else None
  })

  /** プログラムカウンタ */
  val pc = RegInit(params.instructionStart.U(64.W))

  /** フェッチの停止と理由 */
  val waiting = RegInit(WaitingReason.None)

  var nextPC = pc
  var nextWait = waiting
  for (i <- 0 until params.runParallel) {
    val decoder = io.fetchBuffer.decoder(i)
    val cache = io.cache(i)

    cache.address.bits := nextPC

    val branch = Module(new CheckBranch)
    branch.io.instruction := cache.output.bits
    if (params.debug)
      io.branchTypes.get(i) := branch.io.branchType

    // キャッシュからの値があり、待つ必要はなく、JAL命令ではない（JALはアドレスを変えるだけとして処理できて、デコーダ以降を使う必要はない）
    decoder.valid := cache.output.valid && nextWait === WaitingReason.None
    decoder.bits.programCounter := nextPC
    decoder.bits.instruction := cache.output.bits

    cache.address.valid := nextWait === WaitingReason.None

    // 次に停止する必要があるか確認
    nextWait = Mux(
      nextWait =/= WaitingReason.None || !decoder.ready || !decoder.valid,
      nextWait,
      MuxLookup(
        branch.io.branchType.asUInt,
        nextWait,
        Seq(
          BranchType.Branch.asUInt -> WaitingReason.Branch,
          BranchType.JALR.asUInt -> WaitingReason.JALR,
          BranchType.Fence.asUInt -> WaitingReason.Fence,
          BranchType.FenceI.asUInt -> WaitingReason.FenceI,
          BranchType.JAL.asUInt -> Mux(
            branch.io.offset === 0.U,
            WaitingReason.BusyLoop,
            WaitingReason.None
          )
        )
      )
    )
    // PCの更新を確認
    nextPC = nextPC + MuxCase(
      4.U,
      Seq(
        (!decoder.ready || !decoder.valid) -> 0.U,
        (branch.io.branchType === BranchType.JAL) -> branch.io.offset,
        (nextWait =/= WaitingReason.None) -> 0.U
      )
    )

  }
  pc := nextPC
  waiting := nextWait

  // 停止している際の挙動
  when(waiting =/= WaitingReason.None) {
    when(waiting === WaitingReason.Branch || waiting === WaitingReason.JALR) {
      for (e <- io.collectedBranchAddresses.addresses) {
        when(e.valid) {
          waiting := WaitingReason.None
          pc := e.programCounter
        }
      }
    }
    when(waiting === WaitingReason.Fence || waiting === WaitingReason.FenceI) {
      when(io.reorderBufferEmpty && io.loadStoreQueueEmpty) {
        waiting := WaitingReason.None
        pc := pc + 4.U
      }
    }
    when(waiting === WaitingReason.BusyLoop) {

//      /** 1クロック遅らせるだけ */
//      waiting := WaitingReason.None
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
  (new ChiselStage).emitVerilog(
    new Fetch(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
