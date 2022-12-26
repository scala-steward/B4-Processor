package b4processor.connections

import chisel3._
import chisel3.util._

class ReorderBuffer2RegisterFile extends Bundle {
  val destinationRegister = UInt(5.W)
  val value = UInt(64.W)
}
