package b4processor.modules.branch_output_collector

import b4processor.Parameters
import b4processor.connections.{CollectedOutput, BranchOutput, OutputValue}
import chisel3._

class BranchOutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = new CollectedBranchAddresses
    val executor = Vec(params.runParallel, Input(new BranchOutput))
  })
  for (i <- 0 until params.runParallel) {
    io.fetch.addresses(i) := RegNext(io.executor(i))
  }
}

class CollectedBranchAddresses(implicit params: Parameters) extends Bundle {
  val addresses = Vec(params.runParallel, Output(new BranchOutput))
}
