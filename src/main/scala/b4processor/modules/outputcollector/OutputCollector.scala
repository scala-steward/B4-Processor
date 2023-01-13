package b4processor.modules.outputcollector

import b4processor.Parameters
import b4processor.connections.{CollectedOutput, OutputValue}
import b4processor.utils.{B4RRArbiter, FIFO}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class OutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val outputs = new CollectedOutput
    val executor = Flipped(Irrevocable(new OutputValue))
    val dataMemory = Flipped(Irrevocable(new OutputValue))
    val csr = Flipped(Vec(params.threads, Irrevocable(new OutputValue)))
    val isError = Input(Vec(params.threads, Bool()))
  })

  val threadsOutputQueue =
    Seq.fill(params.threads)(Module(new FIFO(3)(new OutputValue)))
  val threadsArbiter =
    Seq.fill(params.threads)(Module(new B4RRArbiter(new OutputValue, 3)))

  for (tid <- 0 until params.threads) {
    threadsArbiter(tid).io.in(0) <> io.executor
    threadsArbiter(tid).io
      .in(0)
      .valid := io.executor.bits.tag.threadId === tid.U
    threadsArbiter(tid).io.in(1) <> io.dataMemory
    threadsArbiter(tid).io
      .in(1)
      .valid := io.dataMemory.bits.tag.threadId === tid.U
    threadsArbiter(tid).io.in(2) <> io.csr(tid)

    threadsOutputQueue(tid).input <> threadsArbiter(tid).io.out
    threadsOutputQueue(tid).flush := io.isError(tid)
  }

  val outputArbitar = Module(new B4RRArbiter(new OutputValue, params.threads))
  for (tid <- 0 until params.threads)
    outputArbitar.io.in(tid) <> threadsOutputQueue(tid).output

  io.outputs.outputs.valid := outputArbitar.io.out.valid
  io.outputs.outputs.bits := outputArbitar.io.out.bits
  outputArbitar.io.out.ready := true.B
}

object OutputCollector extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitSystemVerilog(new OutputCollector)
}
