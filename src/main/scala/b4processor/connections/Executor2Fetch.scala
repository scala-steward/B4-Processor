package b4processor.connections

import chisel3._
import chisel3.util._

class Executor2Fetch extends ReadyValidIO(new Bundle {
  val programCounter = SInt(64.W)
})
