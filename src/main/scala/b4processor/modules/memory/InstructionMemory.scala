package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.InstructionMemory2Cache
import chisel3._
import chisel3.util._

class InstructionMemory(memoryInit: => Seq[UInt])(implicit params: Parameters) extends Module {
  val io = IO(new InstructionMemory2Cache)

  val memory = VecInit(memoryInit)

  for (i <- 0 until params.fetchWidth) {
    io.output(i) := Cat(
      memory(io.address.asUInt + (i * 4 + 3).U),
      memory(io.address.asUInt + (i * 4 + 2).U),
      memory(io.address.asUInt + (i * 4 + 1).U),
      memory(io.address.asUInt + (i * 4).U)
    )
  }
}
