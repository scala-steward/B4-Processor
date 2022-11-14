package b4processor.modules.memory

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessWidth
import b4processor.utils.Tag
import chisel3._

class MemoryWriteTransaction(implicit params: Parameters) extends Bundle {
  val outputTag = new Tag()
  val address = UInt(64.W)
  val data = UInt(64.W)
  val mask = UInt(8.W)
}

class MemoryReadTransaction(implicit params: Parameters) extends Bundle {
  val outputTag = new Tag()
  val address = UInt(64.W)
  val size = new MemoryAccessWidth.Type()
}

class InstructionFetchTransaction(implicit params: Parameters) extends Bundle {
  val address = UInt(64.W)
  val burstLength = UInt(8.W)
}
