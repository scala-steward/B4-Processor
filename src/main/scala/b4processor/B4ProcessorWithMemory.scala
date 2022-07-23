package b4processor

import b4processor.modules.memory.{DataMemory, InstructionMemory}
import b4processor.utils.InstructionUtil
import chisel3._
import chisel3.stage.ChiselStage

class B4ProcessorWithMemory(instructions: String)(implicit params: Parameters)
    extends Module {
  val io = IO(new Bundle {
    val registerFileContents =
      if (params.debug) Some(Output(Vec(31, UInt(64.W)))) else None
  })
  val core = Module(new B4Processor)
  val instructionMemory = Module(
    new InstructionMemory(
      InstructionUtil.fromFile8bit(s"${instructions}.text.hex")
    )
  )
  val dataMemory = Module(new DataMemory(s"${instructions}.data.hex"))
  core.io.instructionMemory <> instructionMemory.io
  core.io.dataMemory.lsq <> dataMemory.io.dataIn
  core.io.dataMemory.output <> dataMemory.io.dataOut
  if (params.debug)
    core.io.registerFileContents.get <> io.registerFileContents.get
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
    new B4ProcessorWithMemory("riscv-sample-programs/fibonacci_c/fibonacci_c")
  )
}
