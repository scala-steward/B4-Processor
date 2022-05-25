package b4processor.modules.executor

import b4processor.Parameters
import chisel3._

class ExecutionRegisterBypassForTest(implicit val params: Parameters) extends Bundle {
  val destinationTag = Output(UInt(params.tagWidth.W))
  val value = Output(SInt(64.W))
  val valid = Output(Bool())
}
