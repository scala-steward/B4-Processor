package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

class ReservationStation2Executor(implicit params: Parameters) extends Bundle {
  val destinationTag = UInt(params.tagWidth.W)
  val value1 = UInt(64.W)
  val value2 = UInt(64.W)
  val function3 = UInt(3.W)
  val immediateOrFunction7 = UInt(12.W)
  val opcode = UInt(7.W)
  val programCounter = SInt(64.W)
  val branchID = UInt(params.branchBufferSize.W)
}
