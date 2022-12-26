package b4processor.modules.csr

import chisel3._
import chisel3.util._
class CycleCounter extends Module {
  val count = IO(Output(UInt(64.W)))
  private val c = RegInit(0.U(64.W))
  c := c + 1.U
  count := c
}
