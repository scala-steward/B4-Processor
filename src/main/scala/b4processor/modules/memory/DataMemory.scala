package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.{LoadStoreQueue2Memory, Memory2ReorderBuffer}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class DataMemory(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn = Flipped(new LoadStoreQueue2Memory)
    val dataOut = new Memory2ReorderBuffer
  })

  val mem = SyncReadMem(math.pow(2, params.tagWidth).toInt, UInt(64.W))

  io.dataOut := DontCare
  io.dataIn.ready := true.B

  when(io.dataIn.valid) {
    val rdwrPort = mem((io.dataIn.bits.address).asUInt)
    when(io.dataIn.bits.opcode === "b0000011".U) {
      // Load
      rdwrPort := MuxLookup(io.dataIn.bits.function3, 0.U, Seq(
        "b000".U -> Mux(io.dataIn.bits.data(7), Cat(0.U(56.W), io.dataIn.bits.data(7, 0)), Cat(1.U(56.W), io.dataIn.bits.data(7, 0))),
        "b001".U -> Mux(io.dataIn.bits.data(15), Cat(0.U(48.W), io.dataIn.bits.data(15, 0)), Cat(1.U(48.W), io.dataIn.bits.data(15, 0))),
        "b010".U -> Mux(io.dataIn.bits.data(31), Cat(0.U(32.W), io.dataIn.bits.data(31, 0)), Cat(1.U(32.W), io.dataIn.bits.data(31, 0))),
        "b011".U -> io.dataIn.bits.data,
        "b100".U -> Cat(0.U(56.W), io.dataIn.bits.data(7, 0)),
        "b101".U -> Cat(0.U(48.W), io.dataIn.bits.data(15, 0)),
        "b110".U -> Cat(0.U(32.W), io.dataIn.bits.data(31, 0))
      ))
    }.otherwise {
      // Store
      io.dataOut.bits.data := MuxLookup(io.dataIn.bits.function3, 0.U, Seq(
        "b000".U -> Mux(rdwrPort(7), Cat(0.U(56.W), rdwrPort(7, 0)), Cat(1.U(56.W), rdwrPort(7, 0))),
        "b001".U -> Mux(rdwrPort(15), Cat(0.U(48.W), rdwrPort(15, 0)), Cat(1.U(48.W), rdwrPort(15, 0))),
        "b010".U -> Mux(rdwrPort(31), Cat(0.U(32.W), rdwrPort(31, 0)), Cat(1.U(32.W), rdwrPort(31, 0))),
        "b011".U -> rdwrPort,
      ))
    }
    io.dataOut.bits.tag := io.dataIn.bits.tag
  }
  io.dataOut.valid := io.dataIn.valid
}

object DataMemoryElaborate extends App {
  implicit val params = Parameters(numberOfDecoders = 1, numberOfALUs = 1, maxRegisterFileCommitCount = 1, tagWidth = 4)
  (new ChiselStage).emitVerilog(new DataMemory, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
