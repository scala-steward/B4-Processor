package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.connections.{Decoder2LoadStoreQueue, Execution2LoadStoreQueue, ExecutionRegisterBypass, LoadStoreQueue2Memory, LoadStoreQueue2ReorderBuffer}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class LoadStoreQueue(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.numberOfDecoders, Flipped(new Decoder2LoadStoreQueue))
    val alus = Vec(params.numberOfALUs, Flipped(Output(new Execution2LoadStoreQueue())))
    val reorderbuffer = Input(new LoadStoreQueue2ReorderBuffer())
    val memory = Vec(params.maxLSQ2MemoryinstCount, new LoadStoreQueue2Memory)

    val head = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    // LSQのエントリ数はこのままでいいのか
  })

  val defaultEntry = {
    val entry = Wire(new LoadStoreQueueEntry)
    entry.opcode := 0.U
    entry.Readyaddress := false.B
    entry.address := 0.S
    entry.Readydata := false.B
    entry.tag := 0.U
    entry.data := 0.U
    entry.function3 := 0.U
    entry.programCounter := 0.S
    entry.R := false.B // 命令実効済か否か
    entry.ReadyReorderSign := false.B
    entry
  }

  val head = RegInit(0.U(params.tagWidth.W))
  val tail = RegInit(0.U(params.tagWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt)(defaultEntry)))
  var insertIndex = head

  /** デコードした命令をLSQに加えるかどうか確認し，l or s 命令ならばエンキュー */
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    io.decoders(i).ready := tail =/= insertIndex + 1.U
    val decodevalid = Mux(io.decoders(i).ready && io.decoders(i).valid && (decoder.bits.opcode === "b0000011".U || decoder.bits.opcode === "b0100011".U), true.B, false.B)

    /**
     * 現状，(LSQの最大エントリ数 = リオーダバッファの最大エントリ数)であり，
     * プロセッサで同時実行可能な最大命令数がリオーダバッファのエントリ番号数(dtag数)であることから，
     * エンキュー時の命令待機は必要ないが，LSQのエントリ数を減らした場合，必要
     */
    when(decodevalid) {
      buffer(insertIndex) := {
        val entry = Wire(new LoadStoreQueueEntry)
        entry.opcode := decoder.bits.opcode
        entry.Readyaddress := false.B
        entry.address := 0.S
        entry.Readydata := false.B
        entry.tag := decoder.bits.stag2
        entry.data := decoder.bits.value
        entry.function3 := decoder.bits.function3
        entry.programCounter := decoder.bits.programCounter
        entry.R := true.B
        entry.ReadyReorderSign := false.B
        entry
      }
    }
    insertIndex = Mux(insertIndex === (math.pow(2, params.tagWidth).toInt.U-1.U) && decodevalid, 0.U, insertIndex + decodevalid.asUInt)
  }

  head := insertIndex

  /** オペランドバイパスのタグorPCが対応していた場合は，ALUを読み込む */

  for (i <- 0 until params.numberOfALUs) {
    val alu = io.alus(i)
    for (buf <- buffer) {
      when(alu.valid && buf.tag === alu.destinationTag) {
        when(!buf.Readyaddress && io.alus(i).ProgramCounter === buf.programCounter) {
          buf.address := alu.value.asSInt
          buf.Readyaddress := true.B
        }
        when(!buf.Readydata && !(io.alus(i).ProgramCounter === buf.programCounter)) {
          // only Store
          buf.data := alu.value.asUInt
          buf.Readydata := true.B
        }
        // printf(p"address = ${buf.address}\n")
        // printf(p"data = ${buf.data}\n")
      }
    }
    // 2重構造
  }

  for(i <- 0 until params.maxRegisterFileCommitCount) {
    when(io.reorderbuffer.valid(i)) {
      for (buf <- buffer) {
        when(buf.R && (io.reorderbuffer.programCounter(i) === buf.programCounter)) {
          buf.ReadyReorderSign := true.B
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
  // val opcodeFormatChecker = Module(new OpcodeFormatChecker)
  // Overlap      : 先行する命令の実効アドレスとの被りがあるかのフラグ (T:あり　F:なし)
  // Address      : 送出対象の命令のアドレスを格納
  // EmissionFlag : LSQから送出するか否かのフラグ
  val Overlap = WireInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(false.B)))
  val Address = WireInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(0.S(64.W))))
  val EmissionFlag = WireInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(false.B)))

  // emissionindex : 送出可能か調べるエントリを指すindex
  // nexttail      : 1クロック分の送出確認後，動かすtailのエントリを指すindex
  var emissionindex = tail
  var nexttail = tail

  // カウンタ変数にjは使えない？ & 2重ループforにtailやindexを使えない
  for (i <- 0 until params.maxRegisterFileCommitCount) {
    emissionindex :=  Mux(emissionindex === (math.pow(2, params.tagWidth).toInt.U-1.U), 0.U, emissionindex + 1.U) // リングバッファ

    io.memory(i).valid := io.memory(i).ready && (head =/= tail)
    io.memory(i).bits.address := 0.S
    io.memory(i).bits.tag := 0.U
    io.memory(i).bits.data := 0.U
    io.memory(i).bits.opcode := 0.U
    io.memory(i).bits.function3 := 0.U

    Address(i) := buffer(emissionindex).programCounter
    Overlap(i) := Mux(i.asUInt === 0.U, false.B,
      Address.map(_ === buffer(emissionindex).address).fold(false.B)(_ || _))
    // EmissionFlag(i) :=  io.memory(i).ready && ("loadの送出条件" || "storeの送出条件")
    EmissionFlag(i) := io.memory(i).valid && (((buffer(emissionindex).opcode === "b0000011".U) && buffer(emissionindex).Readyaddress && !Overlap(i) && buffer(emissionindex).R) ||
      (buffer(emissionindex).opcode === "b0100011".U && buffer(emissionindex).Readyaddress && buffer(emissionindex).Readydata && buffer(emissionindex).ReadyReorderSign && buffer(emissionindex).R))

    // 送出実行
    when(EmissionFlag(i)) {
      io.memory(i).bits.tag := buffer(emissionindex).tag
      io.memory(i).bits.data := buffer(emissionindex).data
      io.memory(i).bits.address := buffer(emissionindex).address
      io.memory(i).bits.opcode := buffer(emissionindex).opcode
      io.memory(i).bits.function3 := buffer(emissionindex).function3
      buffer(emissionindex).R := false.B
    }
    // tailから送出しない命令までの命令数をカウント
    nexttail := Mux(EmissionFlag.fold(true.B)(_ && _), nexttail + 1.U, nexttail)
    // printf(p"address(0) = ${Address(0)}\n")
    // printf(p"emissionIndex = ${emissionindex}\n")
     printf(p"Emission(0) = ${EmissionFlag(0)}\n")
    // printf(p"(0) = ${io.memory(i).valid && (((buffer(emissionindex).opcode === "b0000011".U) && buffer(emissionindex).Readyaddress && !Overlap(i) && buffer(emissionindex).R) ||
    //  (buffer(emissionindex).opcode === "b0100011".U && buffer(emissionindex).Readyaddress && buffer(emissionindex).Readydata && buffer(emissionindex).ReadyReorderSign && buffer(emissionindex).R))}\n")
    printf(p"nexttail = ${nexttail}\n")
    printf(p"1 or 0 = ${EmissionFlag.fold(true.B)(_ && _)}\n")

  }
  tail := nexttail
  printf(p"tail = ${tail}\n\n")

  // デバッグ
  if (params.debug) {
    io.head.get := head
    io.tail.get := tail
    // printf(p"buffer(0).pc = ${buffer(0).programCounter}\n")
    // printf(p"buffer(0).opcode = ${buffer(0).opcode}\n")
    // printf(p"buffer(0).address = ${buffer(0).address}\n")
    // printf(p"buffer(0).data = ${buffer(0).data}\n")
    // printf(p"buffer(0).Rrs = ${buffer(0).ReadyReorderSign}\n")
    // printf(p"buffer(0).R = ${buffer(0).R}\n")
    // printf(p"buffer(0).Rd = ${buffer(0).Readydata}\n")
    // printf(p"buffer(0).Ra = ${buffer(0).Readyaddress}\n\n")

  }
}

object  LoadStoreQueueElabolate extends App {
  implicit val params = Parameters(numberOfDecoders = 1, numberOfALUs = 1, maxLSQ2MemoryinstCount = 2, tagWidth = 4)
  (new ChiselStage).emitVerilog(new LoadStoreQueue, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}