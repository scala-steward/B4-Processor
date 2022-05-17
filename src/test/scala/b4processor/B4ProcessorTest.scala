package b4processor

import b4processor.modules.memory.InstructionMemory
import b4processor.utils.InstructionUtil
import chiseltest._
import chisel3._
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorWrapper(instructions: Seq[UInt])(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val registerFileContents = if (params.debug) Some(Output(Vec(31, UInt(64.W)))) else None
  })
  val core = Module(new B4Processor)
  val instructionMemory = Module(new InstructionMemory(instructions))
  core.io.instructionMemory <> instructionMemory.io
  if (params.debug)
    core.io.registerFileContents.get <> io.registerFileContents.get
}

class B4ProcessorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  implicit val defaultParams = Parameters(debug = true)

  it should "compile" in {
    test(new B4ProcessorWrapper(Seq(0.U))) { c => }
  }

  it should "execute branch with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/branch/branch.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(100)
      }
  }

  it should "execute fibonacci with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(200)
        c.io.registerFileContents.get(9).expect(55)
      }
  }

  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex")))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(200)
        c.io.registerFileContents.get(9).expect(55)
      }
  }

  it should "execute fibonacci with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 4)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(200)
        c.io.registerFileContents.get(9).expect(55)
      }
  }
}
