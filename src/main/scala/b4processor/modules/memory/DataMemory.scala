package b4processor.modules.memory

import chisel3._

class DataMemory extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val write = Input(Bool())
    val address = Input(UInt(10.W))
    val dataIn = Input(UInt(64.W))
    val dataOut = Output(UInt(64.W))
  })

  val mem = SyncReadMem(1024, UInt(64.W))
  io.dataOut := DontCare
  when(io.enable) {
    val rdwrPort = mem(io.address)
    when(io.write) {
      rdwrPort := io.dataIn
    }.otherwise {
      io.dataOut := rdwrPort
    }
  }
}
