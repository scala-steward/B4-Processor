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
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true, tagWidth = 4)

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

  // フィボナッチ数列の計算が同時発行数1でできる
  it should "execute fibonacci with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(150)
        c.io.registerFileContents.get(5).expect(55)
      }
  }

  // フィボナッチ数列の計算が同時発行数2でできる
  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex")))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(100)
        c.io.registerFileContents.get(5).expect(55)
      }
  }

  // フィボナッチ数列の計算が同時発行数4でできる
  it should "execute fibonacci with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 4)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(100)
        c.io.registerFileContents.get(5).expect(55)
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

  // 並列実行できそうな大量のadd命令を同時発行数1で試す
  it should "execute many_add with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(0).expect(8)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数2で試す
  it should "execute many_add with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(fetchWidth = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(0).expect(8)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数4で試す
  it should "execute many_add with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 4, fetchWidth = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(20)
        c.io.registerFileContents.get(0).expect(8)
      }
  }

  //  // 並列実行できそうな大量のadd命令を同時発行数8で試す
  //  it should "execute many_add with 8 parallel" in {
  //    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 8, fetchWidth = 8)))
  //      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
  //        c.clock.step(20)
  //        c.io.registerFileContents.get(0).expect(8)
  //      }
  //  }

}
