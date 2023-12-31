package b4smt.connections

import chisel3._
import chisel3.util._

class InstructionCache2Fetch extends Bundle {
  val address = Flipped(Valid(UInt(64.W)))
  val output = Valid(UInt(32.W))
}
