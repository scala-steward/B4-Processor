package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.connections.{
  CollectedOutput,
  Decoder2LoadStoreQueue,
  Executor2LoadStoreQueue,
  LoadStoreQueue2Memory,
  LoadStoreQueue2ReorderBuffer
}
import b4processor.modules.ourputcollector.OutputCollector
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth._
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class LoadStoreQueue(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders =
      Vec(params.runParallel, Flipped(Decoupled(new Decoder2LoadStoreQueue())))
    val outputCollector = Flipped(new CollectedOutput)
    val reorderBuffer = Input(new LoadStoreQueue2ReorderBuffer())
    val flush = Input(Bool())
    val memory =
      Vec(params.maxRegisterFileCommitCount, new LoadStoreQueue2Memory)
    val isEmpty = Output(Bool())

    val head =
      if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W)))
      else None
    val tail =
      if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W)))
      else None
    // LSQのエントリ数はこのままでいいのか
  })

  val defaultEntry = LoadStoreQueueEntry.default

  val head = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  val tail = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  io.isEmpty := head === tail

  val buffer = RegInit(
    VecInit(
      Seq.fill(math.pow(2, params.loadStoreQueueIndexWidth).toInt)(defaultEntry)
    )
  )
  var insertIndex = head

  /** デコードした命令をLSQに加えるかどうか確認し，l or s 命令ならばエンキュー */
  for (i <- 0 until params.runParallel) {
    val decoder = io.decoders(i)
    decoder.ready := tail =/= insertIndex + 1.U
    val entryValid = decoder.ready && decoder.valid

    /** 現状，(LSQの最大エントリ数 = リオーダバッファの最大エントリ数)であり，
      * プロセッサで同時実行可能な最大命令数がリオーダバッファのエントリ番号数(dtag数)であることから，
      * エンキュー時の命令待機は必要ないが，LSQのエントリ数を減らした場合，必要
      */
    when(entryValid) {
      //      printf("isLoad = %d\n", decoder.bits.opcode === LOAD)
      buffer(insertIndex) := LoadStoreQueueEntry.validEntry(
        // opcode = 1(load), 0(store) (bit数削減)
        accessInfo = decoder.bits.accessInfo,
        addressAndStoreResultTag = decoder.bits.addressAndLoadResultTag,
        storeDataTag = decoder.bits.storeDataTag,
        storeData = decoder.bits.storeData,
        storeDataValid = decoder.bits.storeDataValid
      )
    }
    insertIndex = insertIndex + entryValid.asUInt
  }

  head := insertIndex

  /** オペランドバイパスのタグorPCが対応していた場合は，ALUを読み込む */

  for (output <- io.outputCollector.outputs) {
    for (buf <- buffer) {
      when(
        (output.validAsResult || output.validAsLoadStoreAddress) && buf.valid
      ) {
        when(buf.addressAndLoadResultTag === output.tag && !buf.addressValid) {
          buf.address := output.value.asSInt
          buf.addressValid := true.B
        }
      }
      when(output.validAsResult && buf.valid) {
        when(buf.storeDataTag === output.tag && !buf.storeDataValid) {
          // only Store
          buf.storeData := output.value
          buf.storeDataValid := true.B
        }
      }
    }
    // 2重構造
  }

  for (i <- 0 until params.maxRegisterFileCommitCount) {
    when(io.reorderBuffer.valid(i)) {
      for (buf <- buffer) {
        when(
          buf.valid && (io.reorderBuffer
            .destinationTag(i) === buf.addressAndLoadResultTag)
        ) {
          buf.readyReorderSign := true.B
        }
      }
    }
  }

  /** Ra = 1 && R=0の先行するストア命令と実効アドレスが被っていなければ ロード命令をメモリに送出 送出後, R=1
    */

  /** Ra = Rd = 1 && リオーダバッファから命令送出信号が来れば ストア命令をメモリに送出 送出後, R=1
    */
  // Overlap : 先行する命令の実効アドレスとの被りがあるかのフラグ (T:あり　F:なし)
  // Address : 送出対象の命令のアドレスを格納
  val Overlap = WireInit(
    VecInit(Seq.fill(params.maxRegisterFileCommitCount)(false.B))
  )
  val Address = WireInit(
    VecInit(Seq.fill(params.maxRegisterFileCommitCount)(0.S(64.W)))
  )
  val AddressValid = WireInit(
    VecInit(Seq.fill(params.maxRegisterFileCommitCount)(false.B))
  )

  // emissionindex : 送出可能か調べるエントリを指すindex
  // nexttail      : 1クロック分の送出確認後，動かすtailのエントリを指すindex
  var emissionIndex = tail
  var nextTail = tail
  var lastOk = true.B

  for (i <- 0 until params.maxRegisterFileCommitCount) {
    val entry = buffer(emissionIndex)
    val mem = io.memory(i)
    mem.bits := DontCare
    mem.valid := false.B

    when(entry.valid) {
      Address(i) := entry.address
      AddressValid(i) := entry.addressValid
      //      Overlap(i) := false.B
      // 先行する命令が持つアドレスの中に被りがある場合，Overlap(i) := true.B
      if (i != 0) {
        for (j <- 0 until i) {
          when(
            Address(j) === buffer(emissionIndex).address || !AddressValid(j)
          ) {
            Overlap(i) := true.B
          }
        }
      }

      // io.memory(i).valid :=  io.memory(i).ready && (head =/= tail) && ("loadの送出条件" || "storeの送出条件")
      mem.valid := (head =/= nextTail) && entry.valid && entry.addressValid &&
        ((entry.info.accessType === Load && !Overlap(i)) ||
          (entry.info.accessType === Store && entry.storeDataValid && entry.readyReorderSign))

      // 送出実行
      when(mem.valid) {
        mem.bits.tag := entry.addressAndLoadResultTag
        mem.bits.data := entry.storeData
        mem.bits.address := entry.address
        mem.bits.accessInfo := entry.info
      }
      when(mem.valid && mem.ready) {
        entry := LoadStoreQueueEntry.default
      }
    }

    // nextTailの更新
    //    printf("%b && (%b || (%b && %b)) = %b\n", i.U === (nextTail - tail), io.memory(i).valid, head =/= nextTail, !buffer(emissionIndex).valid, (i.U === (nextTail - tail)) &&
    //    (io.memory(i).valid || (head =/= nextTail && !buffer(emissionIndex).valid))
    //    )
    lastOk = lastOk &&
      ((mem.valid && mem.ready) || (head =/= nextTail && !entry.valid))
    nextTail = nextTail + Mux(lastOk, 1.U, 0.U)
    emissionIndex = emissionIndex + 1.U // リングバッファ
  }
  tail := nextTail

  // 投機的な実行に失敗した際のエントリの削除
  when(io.flush) {
    for (b <- buffer) {
      when(b.valid) {
        when(b.info.accessType === Load && (!b.addressValid)) {
          b.valid := false.B
        }
        when(
          b.info.accessType === Store && (!b.addressValid || !b.storeDataValid || !b.readyReorderSign)
        ) {
          b.valid := false.B
        }
      }

    }
  }

  // デバッグ
  if (params.debug) {
    io.head.get := head
    io.tail.get := tail
    //            printf(p"io.memory(0) = ${io.memory(0).valid}\n")
    //                printf(p"io.memory(1) = ${io.memory(1).valid}\n")
    //                printf(p"buffer(0).valid = ${buffer(0).valid}\n")
    //                printf(p"buffer(1).valid = ${buffer(1).valid}\n")
    //                printf(p"buffer(0).storeDataValid = ${buffer(0).storeDataValid}\n")
    //                printf(p"buffer(1).storeDataValid = ${buffer(1).storeDataValid}\n")
    //                printf(p"buffer(0).readyReorderSign = ${buffer(0).readyReorderSign}\n")
    //                printf(p"buffer(1).readyReorderSign = ${buffer(1).readyReorderSign}\n")
    //                printf(p"Address(0) = ${Address(0)}\n")
    //                printf(p"Address(1) = ${Address(1)}\n")
    //                printf(p"Overlap(0) = ${Overlap(0)}\n")
    //                printf(p"Overlap(1) = ${Overlap(1)}\n")
    //            printf(p"head = $head\n")
    //            printf(p"tail = $tail\n\n")
  }
}

object LoadStoreQueueElabolate extends App {
  implicit val params = Parameters(maxRegisterFileCommitCount = 2, tagWidth = 4)
  (new ChiselStage).emitVerilog(
    new LoadStoreQueue,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
