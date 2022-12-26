package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.connections.{
  CollectedOutput,
  Decoder2LoadStoreQueue,
  Executor2LoadStoreQueue,
  LoadStoreQueue2Memory,
  LoadStoreQueue2ReorderBuffer,
  ResultType
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
      Vec(
        params.decoderPerThread,
        Flipped(Decoupled(new Decoder2LoadStoreQueue()))
      )
    val outputCollector = Flipped(new CollectedOutput)
    val reorderBuffer = Flipped(
      Vec(
        params.maxRegisterFileCommitCount,
        Valid(new LoadStoreQueue2ReorderBuffer)
      )
    )
    val memory = Decoupled(new LoadStoreQueue2Memory)
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
  for (i <- 0 until params.decoderPerThread) {
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
        address = decoder.bits.address,
        addressValid = decoder.bits.addressValid,
        storeDataTag = decoder.bits.storeDataTag,
        storeData = decoder.bits.storeData,
        storeDataValid = decoder.bits.storeDataValid
      )
    }
    insertIndex = insertIndex + entryValid.asUInt
  }

  head := insertIndex

  /** オペランドバイパスのタグorPCが対応していた場合は，ALUを読み込む */

  private val output = io.outputCollector.outputs
  for (buf <- buffer) {
    when(output.valid && buf.valid) {
      when(output.bits.resultType === ResultType.LoadStoreAddress) {
        when(
          buf.addressAndLoadResultTag === output.bits.tag && !buf.addressValid
        ) {
          buf.address := output.bits.value
          buf.addressValid := true.B
        }
      }
      when(output.bits.resultType === ResultType.Result) {
        when(buf.storeDataTag === output.bits.tag && !buf.storeDataValid) {
          buf.storeData := output.bits.value
          buf.storeDataValid := true.B
        }
      }
    }
  }

  for (rb <- io.reorderBuffer) {
    when(rb.valid) {
      for (buf <- buffer) {
        when(
          buf.valid && (rb.bits.destinationTag === buf.addressAndLoadResultTag)
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
    VecInit(Seq.fill(params.loadStoreQueueCheckLength)(false.B))
  )
  val Address = WireInit(
    VecInit(Seq.fill(params.loadStoreQueueCheckLength)(0.U(64.W)))
  )
  val AddressValid = WireInit(
    VecInit(Seq.fill(params.loadStoreQueueCheckLength)(false.B))
  )
  val EntryValid = WireInit(
    VecInit(Seq.fill(params.loadStoreQueueCheckLength)(false.B))
  )

  // emissionindex : 送出可能か調べるエントリを指すindex
  // nexttail      : 1クロック分の送出確認後，動かすtailのエントリを指すindex
  var nextTail = tail
  var isSet = false.B

  io.memory.bits := DontCare
  io.memory.valid := false.B
  for (i <- 0 until params.loadStoreQueueCheckLength) {
    val checkIndex = tail + i.U
    val checkOk = WireDefault(false.B)
    when(buffer(checkIndex).valid) {
      Address(i) := buffer(checkIndex).address
      AddressValid(i) := buffer(checkIndex).addressValid
      EntryValid(i) := buffer(checkIndex).valid
      //      Overlap(i) := false.B
      // 先行する命令が持つアドレスの中に被りがある場合，Overlap(i) := true.B
      if (i != 0) {
        for (j <- 0 until i) {
          when(EntryValid(j)) {
            when(
              (AddressValid(j) && Address(j) === buffer(
                checkIndex
              ).address) || !AddressValid(j)
            ) {
              Overlap(i) := true.B
            }
          }
        }
      }

      // io.memory(i).valid :=  io.memory(i).ready && (head =/= tail) && ("loadの送出条件" || "storeの送出条件")
      checkOk := (head =/= tail) && buffer(checkIndex).valid && buffer(
        checkIndex
      ).addressValid &&
        ((buffer(checkIndex).info.accessType === Load && !Overlap(i)) ||
          (buffer(checkIndex).info.accessType === Store && buffer(
            checkIndex
          ).storeDataValid && buffer(checkIndex).readyReorderSign))

      io.memory.valid := checkOk | isSet
      // 送出実行
      when(checkOk && !isSet) {
        io.memory.bits.tag := buffer(checkIndex).addressAndLoadResultTag
        io.memory.bits.data := buffer(checkIndex).storeData
        io.memory.bits.address := buffer(checkIndex).address
        io.memory.bits.accessInfo := buffer(checkIndex).info
        when(io.memory.ready) {
          buffer(checkIndex) := LoadStoreQueueEntry.default
        }
      }
    }

    isSet = isSet | checkOk
  }
  when(!buffer(tail).valid && head =/= tail) {
    tail := tail + 1.U
  }

  // デバッグ
  if (params.debug) {
    io.head.get := head
    io.tail.get := tail
  }
}

object LoadStoreQueue extends App {
  implicit val params = Parameters(maxRegisterFileCommitCount = 2, tagWidth = 4)
  (new ChiselStage).emitVerilog(
    new LoadStoreQueue,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
