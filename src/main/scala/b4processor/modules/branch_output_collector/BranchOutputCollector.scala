package b4processor.modules.branch_output_collector

import b4processor.Parameters
import b4processor.connections.{BranchOutput, CollectedOutput, OutputValue}
import b4processor.utils.FIFO
import chisel3._
import chisel3.util._

class BranchOutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = new CollectedBranchAddresses
    val executor = Flipped(Irrevocable(new BranchOutput))
  })
  val fifo = Module(new FIFO(2)(new BranchOutput()))
  fifo.input <> io.executor
  io.fetch.addresses.valid := fifo.output.valid
  io.fetch.addresses.bits := fifo.output.bits
  fifo.output.ready := true.B
}

class CollectedBranchAddresses(implicit params: Parameters) extends Bundle {
  val addresses = Valid(new BranchOutput)
}
