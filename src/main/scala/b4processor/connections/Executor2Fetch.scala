package b4processor.connections

import chisel3._
import chisel3.util._

class Executor2Fetch extends  Bundle {
  val programCounter = SInt(64.W)
  val valid = Bool()
}

