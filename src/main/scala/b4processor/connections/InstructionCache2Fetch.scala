package b4processor.connections

import chisel3._
import chisel3.util._

class InstructionCache2Fetch extends Bundle {
  val address = Input(SInt(64.W))
  val output = Valid(UInt(64.W))
}
