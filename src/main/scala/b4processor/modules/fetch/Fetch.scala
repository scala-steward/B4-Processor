package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{
  Fetch2BranchBuffer,
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

    /** 投機的実行用 分岐先バッファ */
    val branchBuffer = new Fetch2BranchBuffer

    /** リオーダバッファの中身が空である */
    val reorderBufferEmpty = Input(Bool())

    /** ロードストアキューが空である */
    val loadStoreQueueEmpty = Input(Bool())

    /** デコーダ */
    val fetchBuffer = new Fetch2FetchBuffer

    /** デバッグ用 */
    val PC = if (params.debug) Some(Output(SInt(64.W))) else None
  })

  /** プログラムカウンタ */
  val pc = RegInit(params.instructionStart.S(64.W))

  /** フェッチの停止と理由 */
  val waiting = RegInit(WaitingReason.None)

  val isPrediction = RegInit(false.B)

  var nextPC = pc
  var nextWait = waiting

  when(io.branchBuffer.changeAddress.valid) {
    pc := io.branchBuffer.changeAddress.bits
    for (i <- 0 until params.runParallel) {
      val decoder = io.fetchBuffer.decoder(i)
      val cache = io.cache(i)
      val branchBuffer = io.branchBuffer.branches(i)
      val prediction = io.prediction(i)

      prediction.address := DontCare
      prediction.branchID := DontCare

      assert(branchBuffer.ready === false.B)
      branchBuffer.valid := DontCare
      branchBuffer.address := DontCare
      decoder.valid := false.B
      decoder.bits := DontCare
      cache.address := DontCare
      prediction.address := DontCare
      io.fetchBuffer.flush := true.B
      waiting := WaitingReason.None
    }
  }.otherwise {
    io.fetchBuffer.flush := false.B
    for (i <- 0 until params.runParallel) {
      val decoder = io.fetchBuffer.decoder(i)
      val cache = io.cache(i)
      val branchBuffer = io.branchBuffer.branches(i)
      val prediction = io.prediction(i)

      branchBuffer.valid := false.B
      branchBuffer.address := DontCare

      prediction.address := DontCare
      prediction.branchID := DontCare

      cache.address := nextPC
      prediction.address := nextPC

      val branch = Module(new CheckBranch)
      branch.io.instruction := io.cache(i).output.bits

      val isBranch = MuxLookup(
        branch.io.branchType.asUInt,
        false.B,
        Seq(
          BranchType.Branch.asUInt -> true.B,
          BranchType.JALR.asUInt -> true.B
        )
      )

      decoder.valid := io
        .cache(i)
        .output
        .valid && nextWait === WaitingReason.None
      decoder.bits.programCounter := nextPC
      decoder.bits.instruction := cache.output.bits
      decoder.bits.isBranch := isBranch
      decoder.bits.branchID := branchBuffer.branchID

      // 次に停止する必要があるか確認
      nextWait = Mux(
        nextWait =/= WaitingReason.None || !decoder.ready || !decoder.valid,
        nextWait,
        MuxLookup(
          branch.io.branchType.asUInt,
          nextWait,
          Seq(
            BranchType.Fence.asUInt -> WaitingReason.Fence,
            BranchType.FenceI.asUInt -> WaitingReason.FenceI,
            BranchType.JAL.asUInt -> Mux(
              branch.io.offset === 0.S,
              WaitingReason.BusyLoop,
              WaitingReason.None
            )
          )
        )
      )

      // PCの更新を確認
      nextPC = nextPC + MuxCase(
        4.S,
        Seq(
          (!decoder.ready || !decoder.valid) -> 0.S,
          (branch.io.branchType === BranchType.JAL) -> branch.io.offset,
          (branch.io.branchType === BranchType.Branch) -> Mux(
            prediction.prediction,
            branch.io.offset,
            4.S
          ),
          (branch.io.branchType === BranchType.JALR) -> 4.S,
          (nextWait =/= WaitingReason.None) -> 0.S
        )
      )

      when(isBranch && decoder.ready && decoder.valid) {
        branchBuffer.valid := true.B
        branchBuffer.address := nextPC
      }

    }
    pc := nextPC
    waiting := nextWait
  }

  // 停止している際の挙動
  when(waiting =/= WaitingReason.None) {
    when(waiting === WaitingReason.Fence || waiting === WaitingReason.FenceI) {
      when(io.reorderBufferEmpty && io.loadStoreQueueEmpty) {
        waiting := WaitingReason.None
        pc := pc + 4.S
      }
    }
    when(waiting === WaitingReason.BusyLoop) {

      /** 1クロック遅らせるだけ */
      waiting := WaitingReason.None
    }
  }

  if (params.debug) {
    io.PC.get := pc
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
