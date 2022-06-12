package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.{LoadStoreQueue2Memory, DataMemory2ReorderBuffer}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class DataMemory(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn = Flipped(new LoadStoreQueue2Memory)
    val dataOut = new DataMemory2ReorderBuffer
  })

  val mem = SyncReadMem(math.pow(2, params.tagWidth).toInt, UInt(64.W))
  io.dataOut := DontCare

  io.dataIn.ready := true.B
  when(io.dataIn.valid) {
    // FIXME: アドレスを下位28bitのみ使っている
    val rdwrPort = mem(io.dataIn.bits.address.asUInt(27, 0))
    when(io.dataIn.bits.opcode === "b0100011".U) {
      // printf(p"dataIn =${io.dataIn.bits.data}\n")
      // Store
      /** writeの場合，rdwrPortは命令実行時の次クロック立ち上がりでmemoryに書き込み(=ストア命令実行時では値変わらず) */
      rdwrPort := MuxLookup(io.dataIn.bits.function3, 0.U, Seq(
        "b000".U -> Mux(io.dataIn.bits.data(7), Cat(~0.U(56.W), io.dataIn.bits.data(7, 0)), Cat(0.U(56.W), io.dataIn.bits.data(7, 0))),
        "b001".U -> Mux(io.dataIn.bits.data(15), Cat(~0.U(48.W), io.dataIn.bits.data(15, 0)), Cat(0.U(48.W), io.dataIn.bits.data(15, 0))),
        "b010".U -> Mux(io.dataIn.bits.data(31), Cat(~0.U(32.W), io.dataIn.bits.data(31, 0)), Cat(0.U(32.W), io.dataIn.bits.data(31, 0))),
        "b011".U -> io.dataIn.bits.data
      ))
    }.otherwise {
      // Load
      /** readの場合，rdwrPortは命令実行時と同クロック立ち上がりでmemoryから読み込み(=ロード命令実行時に値変更)*/
      io.dataOut.bits.data := MuxLookup(io.dataIn.bits.function3, 0.U, Seq(
        "b000".U -> Mux(rdwrPort(7), Cat(~0.U(56.W), rdwrPort(7, 0)), Cat(0.U(56.W), rdwrPort(7, 0))),
        "b001".U -> Mux(rdwrPort(15), Cat(~0.U(48.W), rdwrPort(15, 0)), Cat(0.U(48.W), rdwrPort(15, 0))),
        "b010".U -> Mux(rdwrPort(31), Cat(~0.U(32.W), rdwrPort(31, 0)), Cat(0.U(32.W), rdwrPort(31, 0))),
        "b011".U -> rdwrPort,
        "b100".U -> Cat(0.U(56.W), rdwrPort(7, 0)),
        "b101".U -> Cat(0.U(48.W), rdwrPort(15, 0)),
        "b110".U -> Cat(0.U(32.W), rdwrPort(31, 0))
      ))
      // printf(p"rdwrPort(7) = ${rdwrPort(7)}\n")
      // printf(p"rdwrPort(7, 0) = ${rdwrPort(7, 0)}\n")
      // printf(p"dataOut = ${io.dataOut.bits.data}\n")
    }
    // printf(p"rdwrPort =${rdwrPort}\n")
    io.dataOut.bits.tag := io.dataIn.bits.tag
  }
  io.dataOut.valid := io.dataIn.bits.opcode === "b0000011".U
  // printf(p"mem(io.dataIn.bits.address.asUInt) = ${mem(io.dataIn.bits.address.asUInt)}\n")
}


object DataMemoryElaborate extends App {
  implicit val params = Parameters(runParallel = 1, maxRegisterFileCommitCount = 1, tagWidth = 4)
  (new ChiselStage).emitVerilog(new DataMemory, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
