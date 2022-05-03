package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.{InstructionCache2Fetch, InstructionMemory2Cache}
import chisel3._

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
    when(io.fetch(i).address === baseAddress + (i * 4).S) {
      io.fetch(i).output.bits := io.memory.output(i)
      io.fetch(i).output.valid := true.B
    }.otherwise {
      io.fetch(i).output.bits := 0.U
      io.fetch(i).output.valid := false.B
    }
  }
}
