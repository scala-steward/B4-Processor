package b4smt.modules.fetch

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import b4smt.Parameters
import b4smt.connections.{
  CSR2Fetch,
  Fetch2BranchPrediction,
  Fetch2FetchBuffer,
  InstructionCache2Fetch,
}
import b4smt.modules.branch_output_collector.CollectedBranchAddresses
import b4smt.modules.fetch.{BranchType, CheckBranch, WaitingReason}
import chiselformal.FormalTools

/** 命令フェッチ用モジュール */
class Fetch(wfiWaitWidth: Int = 10)(implicit params: Parameters)
    extends Module
    with FormalTools {
  val io = IO(new Bundle {

    /** 命令キャッシュ */
    val cache =
      Flipped(new InstructionCache2Fetch)

    /** 分岐予測 */
    val prediction = Vec(params.decoderPerThread, new Fetch2BranchPrediction)

    /** リオーダバッファの中身が空である */
    val reorderBufferEmpty = Input(Bool())

    /** ロードストアキューが空である */
    val loadStoreQueueEmpty = Input(Bool())

    /** 実行ユニットから分岐先の計算結果が帰ってきた */
    val collectedBranchAddresses = Flipped(new CollectedBranchAddresses)

    /** デコーダ */
    val fetchBuffer = new Fetch2FetchBuffer

    /** CSR */
    val csr = Input(new CSR2Fetch)

    val csrReservationStationEmpty = Input(Bool())

    val isError = Input(Bool())
    val interrupt = Input(Bool())
    val icache_flush = Output(Bool())

    val threadId = Input(UInt(log2Up(params.threads).W))

    /** デバッグ用 */
    val PC = if (params.debug) Some(Output(UInt(64.W))) else None
    val nextPC = if (params.debug) Some(Output(UInt(64.W))) else None
    val branchTypes =
      if (params.debug)
        Some(Output(Vec(params.decoderPerThread, new BranchType.Type)))
      else None
  })

  io.icache_flush := false.B

  val checkBranches = Seq.fill(params.decoderPerThread)(Module(new CheckBranch))

  /** プログラムカウンタ */
  val pc = RegInit(params.instructionStart.U(64.W))

  /** フェッチの停止と理由 */
  val waiting = RegInit(WaitingReason.None)

  var nextPC = pc
  var nextWait = waiting
  for (i <- 0 until params.decoderPerThread) {
    val decoder = io.fetchBuffer.toBuffer(i)
    val cache = io.cache.perDecoder(i)

    cache.request.bits := nextPC

    val branch = checkBranches(i)
    branch.io.instruction := cache.response.bits
    if (params.debug)
      io.branchTypes.get(i) := branch.io.branchType

    // キャッシュからの値があり、待つ必要はなく、JAL命令ではない（JALはアドレスを変えるだけとして処理できて、デコーダ以降を使う必要はない）
    val instructionValid =
      cache.response.valid && nextWait === WaitingReason.None
    decoder.valid := instructionValid &&
      (branch.io.branchType =/= BranchType.mret && branch.io.branchType =/= BranchType.Fence && branch.io.branchType =/= BranchType.FenceI && branch.io.branchType =/= BranchType.Wfi) &&
      !io.isError
    decoder.bits.programCounter := nextPC
    decoder.bits.instruction := cache.response.bits

    cache.request.valid := nextWait === WaitingReason.None

    // 次に停止する必要があるか確認
    nextWait = Mux(
      nextWait =/= WaitingReason.None || !decoder.ready || !instructionValid,
      nextWait,
      MuxLookup(branch.io.branchType, nextWait)(
        Seq(
          BranchType.Branch -> WaitingReason.Branch,
          BranchType.JALR -> WaitingReason.JALR,
          BranchType.Fence -> WaitingReason.Fence,
          BranchType.FenceI -> WaitingReason.FenceI,
          BranchType.JAL -> Mux(
            branch.io.offset === 0.S,
            WaitingReason.BusyLoop,
            WaitingReason.None,
          ),
          BranchType.mret -> WaitingReason.mret,
          BranchType.Wfi -> WaitingReason.WaitForInterrupt,
        ),
      ),
    )
    // PCの更新を確認
    nextPC = (nextPC.asSInt + MuxCase(
      4.S,
      Seq(
        (!decoder.ready || !decoder.valid) -> 0.S,
        (branch.io.branchType === BranchType.JAL) -> branch.io.offset,
        (branch.io.branchType === BranchType.Branch) -> 0.S,
        (nextWait =/= WaitingReason.None) -> 0.S,
        (branch.io.branchType === BranchType.Next2) -> 2.S,
      ),
    )).asUInt
  }
  pc := nextPC
  waiting := nextWait

  io.cache.requestNext.valid := true.B
  io.cache.requestNext.bits := nextPC

  val wfiCnt = Reg(UInt(wfiWaitWidth.W))

  val lastWaiting = RegNext(waiting)
  val cnt = RegInit(1.U(8.W))
  // 停止している際の挙動
  when(waiting =/= WaitingReason.None) {
    when(lastWaiting === waiting) {
      when(cnt === 0.U) {
        //        printf("Something may be wrong in fetch...\n")
      }.otherwise {
        cnt := cnt + 1.U
      }
    }.otherwise {
      cnt := 1.U
    }
    when(waiting === WaitingReason.Branch || waiting === WaitingReason.JALR) {
      val e = io.collectedBranchAddresses.addresses
      when(e.valid && e.bits.threadId === io.threadId) {
        waiting := WaitingReason.None
        pc := (pc.asSInt + e.bits.programCounterOffset).asUInt
      }

    }
    when(waiting === WaitingReason.Fence || waiting === WaitingReason.FenceI) {
      when(waiting === WaitingReason.FenceI) {
        io.icache_flush := true.B
      }
      when(
        io.reorderBufferEmpty && io.loadStoreQueueEmpty && io.fetchBuffer.empty,
      ) {
        waiting := WaitingReason.None
        pc := pc + 4.U
      }
    }
    when(waiting === WaitingReason.BusyLoop) {
      when(io.interrupt) {
        pc := io.csr.mtvec
        waiting := WaitingReason.None
      }
    }
    when(waiting === WaitingReason.mret) {
      when(io.csrReservationStationEmpty && io.fetchBuffer.empty) {
        waiting := WaitingReason.None
        pc := io.csr.mepc
      }
    }
    when(waiting === WaitingReason.Exception) {
      when(io.csrReservationStationEmpty && io.fetchBuffer.empty) {
        waiting := WaitingReason.None
        when(io.csr.mtvec(1, 0) === 0.U) {
          pc := io.csr.mtvec(63, 2) ## 0.U(2.W)
        }.elsewhen(io.csr.mtvec(1, 0) === 1.U) {
          pc := (io.csr.mtvec(63, 2) + io.csr.mcause(62, 0)) ## 0.U(2.W)
        }
      }
    }
    when(waiting === WaitingReason.WaitForInterrupt) {
      when(io.interrupt) {
        pc := io.csr.mtvec
        waiting := WaitingReason.None
      }
      when(wfiCnt === 0.U) {
        waiting := WaitingReason.None
      }
      wfiCnt := wfiCnt + 1.U
    }
  }

  when(io.isError) {
    waiting := WaitingReason.Exception
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

  // FORMAL
  for (reason <- WaitingReason.all) {
    if (reason != WaitingReason.None) {
      cover(waiting === reason)
      when(pastValid && past(waiting === reason)) {
        cover(
          waiting === WaitingReason.None,
          s"could not come back from $reason",
        )
      }
    }
  }
}

object Fetch extends App {
  implicit val params: Parameters = Parameters()
  ChiselStage.emitSystemVerilogFile(new Fetch)
}
