package b4processor.modules.reorderbuffer

import chisel3._
import chisel3.util._

class ReorderBufferEntry extends Bundle {
  val programCounter = UInt(64.W)
  val destinationRegister = UInt(5.W)
  val ready = Bool()
  val value = UInt(64.W)
}
