package b4processor.modules.ourputcollector

import b4processor.Parameters
import b4processor.connections.{CollectedOutput, OutputValue}
import chisel3._

class OutputCollector(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val outputs = new CollectedOutput
    val executor = Vec(params.runParallel, Flipped(new OutputValue))
    val dataMemory = Flipped(new OutputValue)
  })
  for (i <- 0 until params.runParallel) {
    io.outputs.outputs(i) := io.executor(i)
  }
  io.outputs.outputs(params.runParallel) := io.dataMemory
}
