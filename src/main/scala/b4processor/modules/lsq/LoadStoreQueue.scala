package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.connections.{Decoder2LoadStoreQueue, Execution2LoadStoreQueue, ExecutionRegisterBypass, LoadStoreQueue2Memory, LoadStoreQueue2ReorderBuffer}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class LoadStoreQueue(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.numberOfDecoders, Flipped(new Decoder2LoadStoreQueue()))
    val alus = Vec(params.numberOfALUs, Flipped(Output(new Execution2LoadStoreQueue())))
    val reorderbuffer = new LoadStoreQueue2ReorderBuffer()
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
    entry.programCounter := 0.S
    entry.R := true.B // 命令実効済か否か
    entry
  }

  val head = RegInit(0.U(params.tagWidth.W))
  val tail = RegInit(0.U(params.tagWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt)(defaultEntry)))
  /**
   * Ra = Rd = 1 && リオーダバッファから命令送出信号が来れば
   * ストア命令をメモリに送出 送出後, R=1
   */
  // val opcodeFormatChecker = Module(new OpcodeFormatChecker)
  // Overlap      : 先行する命令の実効アドレスとの被りがあるかのフラグ (T:あり　F:なし)
  // Address      : 送出対象の命令のアドレスを格納
  // StoreOp      : オペコードがストア(=0000011)かどうかのフラグ
  // ReorderSign  : リオーダバッファからストア命令の送出信号があるかどうかのフラグ
  // EmissionFlag : LSQから送出するか否かのフラグ
  val Overlap = RegInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(false.B)))

  /** デコードした命令をLSQに加えるかどうか確認し，l or s 命令ならばエンキュー */
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    io.decoders(i).ready := true.B
    when(tail === insertIndex + 1.U) {
      io.decoders(i).ready := false.B
    }
    val decodevalid = Mux(io.decoders(i).ready === true.B && (decoder.opcode === "b0000011".U || decoder.opcode === "b0100011".U), true.B, false.B)

    /**
     * 現状，(LSQの最大エントリ数 = リオーダバッファの最大エントリ数)であり，
     * プロセッサで同時実行可能な最大命令数がリオーダバッファのエントリ番号数(dtag数)であることから，
     * エンキュー時の命令待機は必要ないが，LSQのエントリ数を減らした場合，必要
     */
    when(decodevalid) {
      buffer(insertIndex) := {
        val entry = Wire(new LoadStoreQueueEntry)
        entry.opcode := decoder.opcode
        entry.Readyaddress := false.B
        entry.address := 0.S
        entry.Readydata := false.B
        entry.tag := decoder.stag2
        entry.data := decoder.value.bits
        entry.programCounter := decoder.programCounter
        entry.R := false.B
        entry
      }
    }
    insertIndex = Mux(insertIndex === (math.pow(2, params.tagWidth).toInt.U - 1.U) && decodevalid, 0.U, insertIndex + decodevalid.asUInt)
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
          buf.data := alu.value.asUInt
          buf.Readydata := true.B
        }
      }
    }
    // 2重構造
  }

  /**
   * Ra = 1 && R=0の先行するストア命令と実効アドレスが被っていなければ
   * ロード命令をメモリに送出 送出後, R=1
   */
  val Address = RegInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(0.S)))
  val StoreOp = WireDefault(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(false.B)))
  val ReorderSign = WireDefault(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(false.B)))
  val EmissionFlag = RegInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(true.B)))
  var insertIndex = head
  io.reorderbuffer.value.ready := true.B
  // emissionindex : 送出可能か調べるエントリを指すindex
  // nexttail      : 1クロック分の送出確認後，動かすtailのエントリを指すindex
  var emissionindex = tail
  var nexttail = tail

  // カウンタ変数にjは使えない？ & 2重ループforにtailやindexを使えない
  for (i <- 0 until params.maxLSQ2MemoryinstCount) {
    emissionindex := Mux(tail + i.U === (math.pow(2, params.tagWidth).toInt.U - 1.U), 0.U, emissionindex + 1.U) // リングバッファ

    io.memory(i).valid := false.B
    io.memory(i).bits.address := 0.S
    io.memory(i).bits.tag := 0.U
    io.memory(i).bits.data := 0.U
    io.memory(i).bits.opcode := 0.U

    StoreOp(i) := buffer(emissionindex).opcode
    Overlap(i) := Mux(i.asUInt === 0.U, false.B,
      Address.map(_ === buffer(emissionindex).address).fold(false.B)(_ || _)
    )
    Address(i) := buffer(emissionindex).address
    ReorderSign(i) := Cat(io.reorderbuffer.programCounter.map(_ === Address(i))).orR
    // EmissionFlag(i) := Mux("loadの送出条件" || "storeの送出条件", true.B, false.B)
    EmissionFlag(i) := Mux((buffer(emissionindex).opcode === "b0100011".U && buffer(emissionindex).Readyaddress && !Overlap(i) && !StoreOp(i) && !buffer(emissionindex).R) ||
      (buffer(emissionindex).opcode === "b0000011".U && buffer(emissionindex).Readyaddress && buffer(emissionindex).Readydata && ReorderSign(i) && !buffer(emissionindex).R),
      true.B, false.B)


    // 送出実行
    when(EmissionFlag(i)) {
      io.memory(i).valid := true.B
      io.memory(i).bits.tag := buffer(emissionindex).tag
      io.memory(i).bits.data := buffer(emissionindex).data
      io.memory(i).bits.address := buffer(emissionindex).address
      io.memory(i).bits.opcode := buffer(emissionindex).opcode
      buffer(emissionindex).R := true.B
    }
    // tailから送出しない命令までの命令数をカウント
    nexttail := Mux(EmissionFlag.fold(true.B)(_ && _), nexttail + 1.U, nexttail)
  }
  tail := nexttail

  // デバッグ
  if (params.debug) {
    io.head.get := head
    io.tail.get := tail
  }
}

object LoadStoreQueueElabolate extends App {
  implicit val params = Parameters(numberOfDecoders = 1, numberOfALUs = 1, maxLSQ2MemoryinstCount = 2, tagWidth = 4)
  (new ChiselStage).emitVerilog(new LoadStoreQueue, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}