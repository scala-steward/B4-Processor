package b4processor.modules.outputcollector

import b4processor.Parameters
import b4processor.connections.{CollectedOutput, OutputValue}
import b4processor.utils.{B4RRArbiter, FIFO}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class OutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val outputs = Vec(params.threads, new CollectedOutput)
    val executor = Flipped(Vec(params.executors, Irrevocable(new OutputValue)))
    val dataMemory = Flipped(Irrevocable(new OutputValue))
    val csr = Flipped(Vec(params.threads, Irrevocable(new OutputValue)))
    val isError = Input(Vec(params.threads, Bool()))
  })

  val threadsOutputQueue =
    Seq.fill(params.threads)(Module(new FIFO(3)(new OutputValue)))
  val threadsArbiter =
    Seq.fill(params.threads)(
      Module(new Arbiter(new OutputValue, params.executors + 2))
    )

  for (tid <- 0 until params.threads) {
    for (i <- 0 until params.executors) {
      threadsArbiter(tid).io.in(i) <> io.executor(i)
      threadsArbiter(tid).io
        .in(i)
        .valid := io
        .executor(i)
        .valid && io.executor(i).bits.tag.threadId === tid.U
    }
    threadsArbiter(tid).io.in(params.executors) <> io.dataMemory
    threadsArbiter(tid).io
      .in(params.executors)
      .valid := io.dataMemory.valid && io.dataMemory.bits.tag.threadId === tid.U
    threadsArbiter(tid).io.in(params.executors + 1) <> io.csr(tid)

    threadsOutputQueue(tid).input <> threadsArbiter(tid).io.out
    threadsOutputQueue(tid).flush := io.isError(tid)
  }

  for (i <- 0 until params.executors) {
    io.executor(i).ready := (0 until params.threads)
      .map(tid =>
        threadsArbiter(tid).io
          .in(i)
          .ready && io.executor(i).bits.tag.threadId === tid.U
      )
      .reduce(_ || _)
  }
  io.dataMemory.ready := (0 until params.threads)
    .map(tid =>
      threadsArbiter(tid).io
        .in(params.executors)
        .ready && io.dataMemory.bits.tag.threadId === tid.U
    )
    .reduce(_ || _)

  for (i <- 0 until params.threads) {
    io.outputs(i).outputs.valid := threadsOutputQueue(i).output.valid
    io.outputs(i).outputs.bits := threadsOutputQueue(i).output.bits
    threadsOutputQueue(i).output.ready := true.B
  }
}

object OutputCollector extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitSystemVerilog(new OutputCollector)
}
