package b4processor

import b4processor.modules.memory.{DataMemory, InstructionMemory}
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
  val dataMemory = Module(new DataMemory)
  core.io.instructionMemory <> instructionMemory.io
  core.io.dataMemory.lsq <> dataMemory.io.dataIn
  core.io.dataMemory.output <> dataMemory.io.dataOut
  if (params.debug)
    core.io.registerFileContents.get <> io.registerFileContents.get
}

class B4ProcessorCompileTest extends AnyFlatSpec with ChiselScalatestTester {

  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true, tagWidth = 4)

  behavior of "B4Processor connections"
  // コンパイルが通ることを確認（信号をつなぎきれていないとエラーになる）
  for (runParallel <- 1 to 3)
    for (maxCommitCount <- 1 to 3)
      for (tagWidth <- 2 to 3)
        it should s"compile runParallel${runParallel} maxCommitCount=${maxCommitCount} tagWidth=${tagWidth}" in {
          test(new B4ProcessorWrapper(Seq(0.U))(defaultParams.copy(runParallel = runParallel, maxRegisterFileCommitCount = maxCommitCount, tagWidth = tagWidth))) { c => }
        }
}

class B4ProcessorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true, tagWidth = 4)

  // branchプログラムが実行できる
  it should "execute branch with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/branch/branch.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(20)
        while (c.io.registerFileContents.get(12).peekInt() != 20)
          c.clock.step()
        c.io.registerFileContents.get(12).expect(20)
        c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数1でできる
  it should "execute fibonacci with no parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(150)
        while (c.io.registerFileContents.get(5).peekInt() != 55)
          c.clock.step()
        c.io.registerFileContents.get(5).expect(55)
        c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数2でできる
  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex")))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(100)
        while (c.io.registerFileContents.get(5).peekInt() != 55)
          c.clock.step()
        c.io.registerFileContents.get(5).expect(55)
        c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数4でできる
  it should "execute fibonacci with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci/fibonacci.32.hex"))(defaultParams.copy(runParallel = 4)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(100)
        while (c.io.registerFileContents.get(5).peekInt() != 55)
          c.clock.step()
        c.io.registerFileContents.get(5).expect(55)
        c.clock.step()
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
        c.clock.setTimeout(40)
        while (c.io.registerFileContents.get(0).peekInt() != 8)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(8)
        c.clock.step()
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数2で試す
  it should "execute many_add with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(fetchWidth = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(40)
        while (c.io.registerFileContents.get(0).peekInt() != 8)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(8)
        c.clock.step()
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数4で試す
  it should "execute many_add with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 4, fetchWidth = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(40)
        while (c.io.registerFileContents.get(0).peekInt() != 8)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(8)
        c.clock.step()
      }
  }

  // タグ幅をとても小さくする（すべてのデコーダが使えない）ような状況でもうまく動作する
  it should "execute many_add with 4 parallel with very low tag width" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 4, fetchWidth = 8, tagWidth = 2)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(40)
        while (c.io.registerFileContents.get(0).peekInt() != 8)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(8)
        c.clock.step()
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数8で試す
  it should "execute many_add with 8 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add/many_add.32.hex"))(defaultParams.copy(runParallel = 8, fetchWidth = 8, maxRegisterFileCommitCount = 10)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(20)
        while (c.io.registerFileContents.get(0).peekInt() != 8)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(8)
        c.clock.step()
      }
  }

  // アウトオブオーダでできそうな命令を同時発行数4で試す
  it should "execute out_of_order with 4 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/many_add_out_of_order/many_add_out_of_order.32.hex"))(defaultParams.copy(runParallel = 4, fetchWidth = 8, maxRegisterFileCommitCount = 8)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.step(15)
        c.io.registerFileContents.get(0).expect(1)
        c.io.registerFileContents.get(1).expect(2)
        c.io.registerFileContents.get(2).expect(3)
        c.io.registerFileContents.get(3).expect(4)
        c.io.registerFileContents.get(4).expect(1)
        c.io.registerFileContents.get(5).expect(2)
        c.io.registerFileContents.get(6).expect(3)
        c.io.registerFileContents.get(7).expect(4)
        c.io.registerFileContents.get(8).expect(1)
        c.io.registerFileContents.get(9).expect(2)
        c.io.registerFileContents.get(10).expect(3)
        c.io.registerFileContents.get(11).expect(4)
      }
  }

  // 単純な値をストアしてロードするプログラム
  it should "run load_store" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/load_store/load_store.32.hex"))(defaultParams.copy(runParallel = 1, maxRegisterFileCommitCount = 1)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(20)
        while (c.io.registerFileContents.get(2).peekInt() != 10)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(0x8000_0000L)
        c.io.registerFileContents.get(1).expect(10)
        c.io.registerFileContents.get(2).expect(10)

        c.clock.step()
      }
  }

  // 単純な値をストアしてロードするプログラム同時発行数2
  it should "run load_store with 2 parallel" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/load_store/load_store.32.hex"))(defaultParams.copy(runParallel = 2)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(20)
        while (c.io.registerFileContents.get(2).peekInt() != 10)
          c.clock.step()
        c.io.registerFileContents.get(0).expect(0x8000_0000L)
        c.io.registerFileContents.get(1).expect(10)
        c.io.registerFileContents.get(2).expect(10)
      }
  }

  // 単純な値をストアしてロードするプログラム
  it should "run fibonacci_c" in {
    test(new B4ProcessorWrapper(InstructionUtil.fromFile32bit("riscv-sample-programs/fibonacci_c/fibonacci_c.32.hex"))(defaultParams.copy(runParallel = 1, maxRegisterFileCommitCount = 1, loadStoreQueueIndexWidth = 2)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.clock.setTimeout(1000)
        while (c.io.registerFileContents.get(2).peekInt() != 13)
          c.clock.step()
        c.io.registerFileContents.get(2).expect(13)
        c.clock.step()
      }
  }
}
