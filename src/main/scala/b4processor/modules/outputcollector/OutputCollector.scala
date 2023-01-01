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
  })

  val executionUnitOutputQueue = Module(new FIFO(2)(new OutputValue))
  executionUnitOutputQueue.input <> io.executor

  val dataMemoryOutputQueue = Module(new FIFO(2)(new OutputValue))
  dataMemoryOutputQueue.input <> io.dataMemory

  val csrArbiter = Module(new B4RRArbiter(new OutputValue, params.threads))
  csrArbiter.io.in <> io.csr
  val csrOutputQueue = Module(new FIFO(2)(new OutputValue))
  csrOutputQueue.input <> csrArbiter.io.out

  val outputArbitar = Module(new B4RRArbiter(new OutputValue, 3))
  outputArbitar.io.in(0) <> executionUnitOutputQueue.output
  outputArbitar.io.in(1) <> dataMemoryOutputQueue.output
  outputArbitar.io.in(2) <> csrOutputQueue.output

  io.outputs.outputs.valid := outputArbitar.io.out.valid
  io.outputs.outputs.bits := outputArbitar.io.out.bits
  outputArbitar.io.out.ready := true.B
}

object OutputCollector extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitSystemVerilog(new OutputCollector)
}
