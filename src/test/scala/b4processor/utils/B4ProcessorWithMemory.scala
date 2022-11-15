package b4processor.utils

import Chisel.Valid
import b4processor.{B4Processor, Parameters}
import chisel3._
import chisel3.stage.ChiselStage

class B4ProcessorWithMemory(instructions: String)(implicit params: Parameters)
    extends Module {
  val io = IO(new Bundle {
    val simulation = Flipped(Valid(UInt(64.W)))
  })
  val core = Module(new B4Processor)
  val axiMemory = Module(new SimpleAXIMemory())
  core.io <> axiMemory.axi
  io.simulation <> axiMemory.simulationSource.input
}

object B4ProcessorWithMemory extends App {
  implicit val params = Parameters(
    debug = true,
    runParallel = 2,
    maxRegisterFileCommitCount = 4,
    tagWidth = 5,
    loadStoreQueueIndexWidth = 3
  )
  (new ChiselStage).emitVerilog(
    new B4ProcessorWithMemory("riscv-sample-programs/fibonacci_c/fibonacci_c"),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
