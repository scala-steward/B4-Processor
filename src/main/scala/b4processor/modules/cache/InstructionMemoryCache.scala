package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.{InstructionCache2Fetch, InstructionMemory2Cache}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class InstructionMemoryCache(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = Vec(params.numberOfDecoders, new InstructionCache2Fetch)
    val memory = Flipped(new InstructionMemory2Cache)
  })

  val baseAddress = io.fetch(0).address

  io.memory.address := baseAddress
  io.fetch(0).output.bits := io.memory.output(0)
  io.fetch(0).output.valid := true.B

  for (i <- 1 until params.numberOfDecoders) {
    io.fetch(i).output := MuxCase(
      {
        val w = Wire(Valid(UInt(32.W)))
        w.bits := 0.U
        w.valid := false.B
        w
      },
      io.memory.output.zipWithIndex.map { case (m, index) =>
        (io.fetch(i).address === baseAddress + (4 * index).S) -> {
          val w = Wire(Valid(UInt(32.W)))
          w.bits := m
          w.valid := true.B
          w
        }
      }
    )
  }
}

object InstructionMemoryCache extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new InstructionMemoryCache(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}