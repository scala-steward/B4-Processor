package b4processor.modules.executor

import b4processor.Parameters
import chisel3.util.ReadyValidIO
import chisel3._

class ReservationStation2ExecutorForTest(implicit params: Parameters) extends ReadyValidIO(new Bundle {
  val destinationTag = UInt(params.tagWidth.W)
  val value1 = SInt(64.W)
  val value2 = SInt(64.W)
  val function3 = UInt(3.W)
  val immediateOrFunction7 = UInt(12.W)
  val opcode = UInt(7.W)
  val programCounter = SInt(64.W)
})
