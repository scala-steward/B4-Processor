package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.common.OpcodeFormatChecker
import b4processor.connections.{Decoder2LoadStoreQueue, ExecutionRegisterBypass, LoadStoreQueue2Memory, LoadStoreQueue2ReorderBuffer}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class LoadStoreQueue(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.numberOfDecoders, Flipped(new Decoder2LoadStoreQueue()))
    val alus = Vec(params.numberOfALUs, Flipped(new ExecutionRegisterBypass()))
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
    entry.address := 0.U
    entry.Readydata := false.B
    entry.tag := 0.U
    entry.data := 0.U
    entry.programCounter := 0.U
    entry.R := true.B // 命令実効済か否か
    entry
  }

  val head = RegInit(0.U(params.tagWidth.W))
  val tail = RegInit(0.U(params.tagWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt)(defaultEntry)))
  var insertIndex = head

  /** デコードした命令をLSQに加えるかどうか確認し，l or s 命令ならばエンキュー */
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    val decodevalid = if(decoder.opcode == "b0000011".U || decoder.opcode == "b0100011".U) true.B else false.B
    when(decodevalid) {
      buffer(insertIndex) := {
        val entry = Wire(new LoadStoreQueueEntry)
        entry.opcode := decoder.opcode
        entry.Readyaddress := false.B
        entry.address := 0.U
        entry.Readydata := false.B
        entry.tag := decoder.stag2
        entry.data := decoder.value
        entry.programCounter := decoder.programCounter
        entry.R := false.B
        entry
      }
    }
    insertIndex = insertIndex + decodevalid.asUInt
  }
  head := insertIndex

  /** オペランドバイパスのタグが対応していた場合は，ALUを読み込む */

  for (i <- 0 until params.numberOfALUs) {
    val alu = io.alus(i)
    for (buf <- buffer) {
      when(alu.valid && !buf.Readyaddress && buf.tag === alu.bits.destinationTag) {
        buf.address := alu.bits.value
        buf.Readyaddress := true.B
      }
    }
    // 2重構造
  }

  /**
   * Ra = 1 && R=0の先行するストア命令と実効アドレスが被っていなければ
   * ロード命令をメモリに送出 送出後, R=1にしてR=1の命令を先頭からリタイアさせる(tailを移動させる)
   */

  /**
   * Ra = Rd = 1 && リオーダバッファから命令送出信号が来れば
   * ストア命令をメモリに送出 送出後, R=1にしてR=1の命令を先頭からリタイアさせる(tailを移動させる)
   */
  // val opcodeFormatChecker = Module(new OpcodeFormatChecker)
  val Valid = RegInit(VecInit(Seq.fill(params.maxLSQ2MemoryinstCount)(false.B)))
  Valid(0) := true.B

  for (i <- 0 until params.maxLSQ2MemoryinstCount) {
    val index = tail + i.U
    // opcodeFormatChecker.io.opcode := buffer(index).opcode
    Valid(i) := if ()
    when(buffer(index).opcode === "b0100011".U && buffer(index).Readyaddress && !buffer(index).R) {
      buffer(index).data := io.memory(i).bits.data
      buffer(index).address := io.memory(i).bits.address
      io.memory(i).valid := true.B
      buffer(index).R := true.B
    }
  }

}