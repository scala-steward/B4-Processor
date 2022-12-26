package b4processor.modules.csr

import b4processor.Parameters
import chisel3._
import chisel3.util._

class RetireCounter(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val retireInCycle = Input(UInt(log2Up(params.maxRegisterFileCommitCount).W))
    val count = Output(UInt(64.W))
  })

  val c = RegInit(0.U(64.W))
  c := c + io.retireInCycle
  io.count := c
}
