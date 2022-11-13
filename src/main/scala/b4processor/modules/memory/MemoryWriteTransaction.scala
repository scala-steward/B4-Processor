package b4processor.modules.memory

import chisel3._

class MemoryWriteTransaction extends Bundle {
  val address = UInt(64.W)
  val data = UInt(64.W)
  val mask = UInt(8.W)
}

class MemoryReadTransaction extends Bundle {
  val address = UInt(64.W)
  val mask = UInt(8.W)
}
