package b4processor.modules.branch_output_collector

import b4processor.Parameters
import b4processor.connections.{BranchOutput, CollectedOutput, OutputValue}
import b4processor.utils.FIFO
import chisel3._
import chisel3.util._

class BranchOutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = Vec(params.threads, new CollectedBranchAddresses)
    val executor = Flipped(Vec(params.executors, Irrevocable(new BranchOutput)))
    val isError = Input(Vec(params.threads, Bool()))
  })
  private val threadFifos =
    Seq.fill(params.threads)(Module(new FIFO(2)(new BranchOutput)))
  private val executorArbiters = Seq.fill(params.threads)(
    Module(new Arbiter(new BranchOutput, params.executors))
  )
  for (tid <- 0 until params.threads) {
    for (e <- 0 until params.executors) {
      executorArbiters(tid).io.in(e) <> io.executor(e)
      executorArbiters(tid).io.in(e).valid :=
        io.executor(e).valid &&
          io.executor(e).bits.threadId === tid.U
    }

  }

  for (e <- 0 until params.executors) {
    io.executor(e).ready := (0 until params.threads)
      .map(tid =>
        executorArbiters(tid).io.in(e).ready
          && io.executor(e).bits.threadId === tid.U
      )
      .reduce(_ || _)
  }

  for (tid <- 0 until params.threads) {
    threadFifos(tid).input <> executorArbiters(tid).io.out
    threadFifos(tid).flush := io.isError(tid)
    io.fetch(tid).addresses.bits := threadFifos(tid).output.bits
    io.fetch(tid).addresses.valid := threadFifos(tid).output.valid
    threadFifos(tid).output.ready := true.B
    threadFifos(tid).input.bits.threadId := tid.U
    io.fetch(tid).addresses.bits.threadId := tid.U
  }

}

class CollectedBranchAddresses(implicit params: Parameters) extends Bundle {
  val addresses = Valid(new BranchOutput)
}
