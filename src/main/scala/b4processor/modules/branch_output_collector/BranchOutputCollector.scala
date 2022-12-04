package b4processor.modules.branch_output_collector

import b4processor.Parameters
import b4processor.connections.{BranchOutput, CollectedOutput, OutputValue}
import chisel3._
import chisel3.util._

class BranchOutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = new CollectedBranchAddresses
    val executor = Flipped(Valid(new BranchOutput))
  })
  io.fetch.addresses := RegNext(io.executor)
}

class CollectedBranchAddresses(implicit params: Parameters) extends Bundle {
  val addresses = Valid(new BranchOutput)
}
