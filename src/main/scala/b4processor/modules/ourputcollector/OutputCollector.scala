package b4processor.modules.ourputcollector

import b4processor.Parameters
import b4processor.connections.{CollectedOutput, OutputValue}
import b4processor.utils.{B4RRArbiter, FIFO}
import chisel3._
import chisel3.util._

class OutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val outputs = new CollectedOutput
    val executor = Flipped(Irrevocable(new OutputValue))
    val dataMemory = Flipped(Irrevocable(new OutputValue))
  })

  val executionUnitOutputQueue = Module(new FIFO(2)(new OutputValue))
  executionUnitOutputQueue.input <> io.executor

  val dataMemoryOutputQueue = Module(new FIFO(2)(new OutputValue))
  dataMemoryOutputQueue.input <> io.dataMemory

  val outputArbitar = Module(new B4RRArbiter(new OutputValue, 2))
  outputArbitar.io.in(0) <> executionUnitOutputQueue.output
  outputArbitar.io.in(1) <> dataMemoryOutputQueue.output

  io.outputs.outputs.valid := outputArbitar.io.out.valid
  io.outputs.outputs.bits := outputArbitar.io.out.bits
  outputArbitar.io.out.ready := true.B
}
