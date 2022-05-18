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

  // コンパイルが通ることを確認（信号をつなぎきれていないとエラーになる）
  it should "compile" in {
    test(new B4ProcessorWrapper(Seq(0.U))) { c => }
  }

  // branchプログラムが実行できる
  it should "execute branch with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/branch/branch.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(100)
      }
  }

  // フィボナッチ数列の計算が並列無しでできる
  it should "execute fibonacci with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(200)
        c.io.registerFileContents.get(9).expect(55)
      }
  }

  // フィボナッチ数列の計算が2並列でできる
  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex")))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(200)
        c.io.registerFileContents.get(9).expect(55)
      }
  }

  // フィボナッチ数列の計算が4並列でできる
  it should "execute fibonacci with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 4)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(200)
        c.io.registerFileContents.get(9).expect(55)
      }
  }

  // call(JALRを使った関数呼び出し)とret(JALRを使った関数からのリターン)がうまく実行できる
  it should "execute call_ret with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/call_ret/call_ret.32.hex")))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(4).expect(1)
        c.io.registerFileContents.get(5).expect(2)
        c.io.registerFileContents.get(6).expect(3)
      }
  }

  // 並列実行できそうな大量のadd命令を並列なしで試す
  it should "execute many_add with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(0).expect(8)
      }
  }

  // 並列実行できそうな大量のadd命令を2並列で試す
  it should "execute many_add with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(fetchWidth = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(0).expect(8)
      }
  }

  // 並列実行できそうな大量のadd命令を4並列で試す
  it should "execute many_add with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 4, fetchWidth = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(0).expect(8)
      }
  }
}
