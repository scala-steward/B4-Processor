package b4processor.modules.branch_output_collector

import b4processor.Parameters
import b4processor.connections.{BranchOutput, CollectedOutput, OutputValue}
import b4processor.utils.FIFO
import chisel3._
import chisel3.util._

class BranchOutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = Vec(params.threads, new CollectedBranchAddresses)
    val executor = Flipped(Irrevocable(new BranchOutput))
    val isError = Input(Vec(params.threads, Bool()))
  })
  private val threadFifos =
    Seq.fill(params.threads)(Module(new FIFO(2)(new BranchOutput)))
  for (tid <- 0 until params.threads) {
    threadFifos(tid).input <> io.executor
    threadFifos(tid).input.valid :=
      io.executor.valid && io.executor.bits.threadId === tid.U
    threadFifos(tid).flush := io.isError(tid)

    io.fetch(tid).addresses.valid := threadFifos(tid).output.valid
    io.fetch(tid).addresses.bits := threadFifos(tid).output.bits
    threadFifos(tid).output.ready := true.B
  }
  io.executor.ready := (0 until params.threads)
    .map(tid =>
      threadFifos(tid).input.ready && io.executor.bits.threadId === tid.U
    )
    .reduce(_ || _)
}

class CollectedBranchAddresses(implicit params: Parameters) extends Bundle {
  val addresses = Valid(new BranchOutput)
}
