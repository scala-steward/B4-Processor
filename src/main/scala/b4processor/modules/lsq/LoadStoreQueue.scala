package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.connections.{CollectedOutput, Decoder2LoadStoreQueue, Executor2LoadStoreQueue, LoadStoreQueue2Memory, LoadStoreQueue2ReorderBuffer}
import b4processor.modules.ourputcollector.OutputCollector
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class LoadStoreQueue(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.runParallel, Flipped(Decoupled(new Decoder2LoadStoreQueue())))
    val outputCollector = Flipped(new CollectedOutput)
    val reorderBuffer = Input(new LoadStoreQueue2ReorderBuffer())
    val memory = Vec(params.maxRegisterFileCommitCount, new LoadStoreQueue2Memory)
    val isEmpty = Output(Bool())

    val head = if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W))) else None
    // LSQのエントリ数はこのままでいいのか
  })

  val defaultEntry = LoadStoreQueueEntry.default
  val LOAD = "b0000011".U
  val STORE = "b0100011".U

  val head = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  val tail = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.loadStoreQueueIndexWidth).toInt)(defaultEntry)))
  var insertIndex = head

  io.isEmpty := head === tail

  /** デコードした命令をLSQに加えるかどうか確認し，l or s 命令ならばエンキュー */
  for (i <- 0 until params.runParallel) {
    val decoder = io.decoders(i)
    io.decoders(i).ready := tail =/= insertIndex + 1.U
    val decoderValid = io.decoders(i).ready && io.decoders(i).valid && decoder.bits.opcode === BitPat("b0?00011")
    // TODO decoderのvalidと機能が一部被っている　

    /**
     * 現状，(LSQの最大エントリ数 = リオーダバッファの最大エントリ数)であり，
     * プロセッサで同時実行可能な最大命令数がリオーダバッファのエントリ番号数(dtag数)であることから，
     * エンキュー時の命令待機は必要ないが，LSQのエントリ数を減らした場合，必要
     */
    when(decoderValid) {
//      printf("isLoad = %d\n", decoder.bits.opcode === LOAD)
      buffer(insertIndex) := LoadStoreQueueEntry.validEntry(
        // opcode = 1(load), 0(store) (bit数削減)
        isLoad = decoder.bits.opcode === LOAD,
        function3 = decoder.bits.function3,

        addressAndStoreResultTag = decoder.bits.addressAndLoadResultTag,

        storeDataTag = decoder.bits.storeDataTag,
        storeData = decoder.bits.storeData,
        storeDataValid = decoder.bits.storeDataValid
      )
    }
    insertIndex = Mux(insertIndex === (math.pow(2, params.tagWidth).toInt.U - 1.U) && decoderValid, 0.U, insertIndex + decoderValid.asUInt)
  }

  head := insertIndex

  /** オペランドバイパスのタグorPCが対応していた場合は，ALUを読み込む */

  for (output <- io.outputCollector.outputs) {
    for (buf <- buffer) {
      when((output.validAsResult || output.validAsLoadStoreAddress) && buf.valid) {
        when(buf.addressAndLoadResultTag === output.tag && !buf.addressValid) {
          buf.address := output.value.asSInt
          buf.addressValid := true.B
        }
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
        when(buf.valid && (io.reorderBuffer.destinationTag(i) === buf.addressAndLoadResultTag)) {
          buf.readyReorderSign := true.B
        }
      }
    }
  }

  /**
   * Ra = 1 && R=0の先行するストア命令と実効アドレスが被っていなければ
   * ロード命令をメモリに送出 送出後, R=1
   */

  /**
   * Ra = Rd = 1 && リオーダバッファから命令送出信号が来れば
   * ストア命令をメモリに送出 送出後, R=1
   */
  // Overlap : 先行する命令の実効アドレスとの被りがあるかのフラグ (T:あり　F:なし)
  // Address : 送出対象の命令のアドレスを格納
  val Overlap = WireInit(VecInit(Seq.fill(params.maxRegisterFileCommitCount)(false.B)))
  val Address = WireInit(VecInit(Seq.fill(params.maxRegisterFileCommitCount)(0.S(64.W))))

  // emissionindex : 送出可能か調べるエントリを指すindex
  // nexttail      : 1クロック分の送出確認後，動かすtailのエントリを指すindex
  var emissionIndex = tail - 1.U
  var nextTail = tail

  for (i <- 0 until params.maxRegisterFileCommitCount) {
    emissionIndex = Mux(emissionIndex === (math.pow(2, params.tagWidth).toInt.U - 1.U), 0.U, emissionIndex + 1.U) // リングバッファ
    io.memory(i).bits.address := 0.S
    io.memory(i).bits.tag := 0.U
    io.memory(i).bits.data := 0.U
    io.memory(i).bits.isLoad := false.B
    io.memory(i).bits.function3 := 0.U

    when(buffer(emissionIndex).valid) {
      Address(i) := buffer(emissionIndex).address
      Overlap(i) := false.B
      // 先行する命令が持つアドレスの中に被りがある場合，Overlap(i) := true.B
      if (i != 0) {
        for (j <- 0 until i) {
          when(Address(j) === buffer(emissionIndex).address) {
            Overlap(i) := true.B
          }
        }
      }

      // io.memory(i).valid :=  io.memory(i).ready && (head =/= tail) && ("loadの送出条件" || "storeの送出条件")
      io.memory(i).valid := io.memory(i).ready && (head =/= tail) && buffer(emissionIndex).valid && buffer(emissionIndex).addressValid &&
        ((buffer(emissionIndex).isLoad && !Overlap(i)) ||
          (!buffer(emissionIndex).isLoad && buffer(emissionIndex).storeDataValid && buffer(emissionIndex).readyReorderSign))

      // 送出実行
      when(io.memory(i).valid) {
        io.memory(i).bits.tag := buffer(emissionIndex).addressAndLoadResultTag
        io.memory(i).bits.data := buffer(emissionIndex).storeData
        io.memory(i).bits.address := buffer(emissionIndex).address
        io.memory(i).bits.isLoad := buffer(emissionIndex).isLoad
        io.memory(i).bits.function3 := buffer(emissionIndex).function3
        buffer(emissionIndex) := LoadStoreQueueEntry.default
      }
    }.otherwise {
      io.memory(i).valid := false.B
    }

    // nextTailの更新
    //    printf("%b && (%b || (%b && %b)) = %b\n", i.U === (nextTail - tail), io.memory(i).valid, head =/= nextTail, !buffer(emissionIndex).valid, (i.U === (nextTail - tail)) &&
    //    (io.memory(i).valid || (head =/= nextTail && !buffer(emissionIndex).valid))
    //    )
    nextTail = nextTail + Mux((i.U === (nextTail - tail)) &&
      (io.memory(i).valid || (head =/= nextTail && !buffer(emissionIndex).valid)), 1.U, 0.U)
  }
  tail := nextTail


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
  (new ChiselStage).emitVerilog(new LoadStoreQueue, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}