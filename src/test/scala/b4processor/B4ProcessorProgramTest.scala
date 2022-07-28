package b4processor

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3._

class B4ProcessorProgramTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true, tagWidth = 4)

  // branchプログラムが実行できる
  it should "execute branch with no parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/branch/branch")(
        defaultParams.copy(runParallel = 1)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(20)
          while (c.io.registerFileContents.get(3).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(3).expect(20)
          c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数1でできる
  it should "execute fibonacci with no parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/fibonacci/fibonacci")(
        defaultParams.copy(runParallel = 1)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(150)
          while (c.io.registerFileContents.get(5).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(5).expect(55)
          c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数2でできる
  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWithMemory("riscv-sample-programs/fibonacci/fibonacci"))
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(100)
          while (c.io.registerFileContents.get(5).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(5).expect(55)
          c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数4でできる
  it should "execute fibonacci with 4 parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/fibonacci/fibonacci")(
        defaultParams
          .copy(runParallel = 4, tagWidth = 5, maxRegisterFileCommitCount = 5)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(100)
          while (c.io.registerFileContents.get(5).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(5).expect(55)
          c.clock.step()
      }
  }

  // フィボナッチ数列の計算が同時発行数8でできる
  it should "execute fibonacci with 8 parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/fibonacci/fibonacci")(
        defaultParams
          .copy(runParallel = 8, tagWidth = 5, maxRegisterFileCommitCount = 8)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(100)
          while (c.io.registerFileContents.get(5).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(5).expect(55)
          c.clock.step()
      }
  }

  // call(JALRを使った関数呼び出し)とret(JALRを使った関数からのリターン)がうまく実行できる
  it should "execute call_ret with 2 parallel" in {
    test(new B4ProcessorWithMemory("riscv-sample-programs/call_ret/call_ret"))
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.step(20)
          c.io.registerFileContents.get(4).expect(1)
          c.io.registerFileContents.get(5).expect(2)
          c.io.registerFileContents.get(6).expect(3)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数1で試す
  it should "execute many_add with no parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/many_add/many_add")(
        defaultParams.copy(runParallel = 1)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(40)
          while (c.io.registerFileContents.get(0).peekInt() != 8)
            c.clock.step()
          c.io.registerFileContents.get(0).expect(8)
          c.clock.step()
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数2で試す
  it should "execute many_add with 2 parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/many_add/many_add")(
        defaultParams.copy(fetchWidth = 8)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(40)
          while (c.io.registerFileContents.get(0).peekInt() != 8)
            c.clock.step()
          c.io.registerFileContents.get(0).expect(8)
          c.clock.step()
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数4で試す
  it should "execute many_add with 4 parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/many_add/many_add")(
        defaultParams.copy(runParallel = 4, fetchWidth = 8)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(40)
          while (c.io.registerFileContents.get(0).peekInt() != 8)
            c.clock.step()
          c.io.registerFileContents.get(0).expect(8)
          c.clock.step()
      }
  }

  // タグ幅をとても小さくする（すべてのデコーダが使えない）ような状況でもうまく動作する
  it should "execute many_add with 4 parallel with very low tag width" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/many_add/many_add")(
        defaultParams.copy(runParallel = 4, fetchWidth = 8, tagWidth = 2)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(40)
          while (c.io.registerFileContents.get(0).peekInt() != 8)
            c.clock.step()
          c.io.registerFileContents.get(0).expect(8)
          c.clock.step()
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数8で試す
  it should "execute many_add with 8 parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/many_add/many_add")(
        defaultParams.copy(
          runParallel = 8,
          fetchWidth = 8,
          maxRegisterFileCommitCount = 10
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(20)
          while (c.io.registerFileContents.get(0).peekInt() != 8)
            c.clock.step()
          c.io.registerFileContents.get(0).expect(8)
          c.clock.step()
      }
  }

  // アウトオブオーダでできそうな命令を同時発行数4で試す
  it should "execute out_of_order with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/many_add_out_of_order/many_add_out_of_order"
      )(
        defaultParams
          .copy(runParallel = 4, fetchWidth = 8, maxRegisterFileCommitCount = 8)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
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
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/load_store/load_store")(
        defaultParams.copy(runParallel = 1, maxRegisterFileCommitCount = 1)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
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
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/load_store/load_store")(
        defaultParams.copy(runParallel = 2)
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(20)
          while (c.io.registerFileContents.get(2).peekInt() != 10)
            c.clock.step()
          c.io.registerFileContents.get(0).expect(0x8000_0000L)
          c.io.registerFileContents.get(1).expect(10)
          c.io.registerFileContents.get(2).expect(10)
      }
  }

  it should "run fibonacci_c" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/fibonacci_c/fibonacci_c"
      )(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(1000)
          while (c.io.registerFileContents.get(2).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(21)
          c.clock.step(10)
      }
  }

  it should "run fibonacci_c with 2 parallel" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/fibonacci_c/fibonacci_c"
      )(
        defaultParams.copy(
          runParallel = 2,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 3
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(500)
          while (c.io.registerFileContents.get(2).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(21)
          c.clock.step(10)
      }
  }

  it should "run fibonacci_c_opt in 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/fibonacci_c_opt/fibonacci_c_opt"
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 6,
          loadStoreQueueIndexWidth = 3
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(1000)
          while (c.io.registerFileContents.get(2).peekInt() == 0)
            c.clock.step()
          c.io.registerFileContents.get(2).expect("x1CFA62F21".U)
          c.clock.step(10)
      }
  }

  it should "run load_plus_arithmetic" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/load_plus_arithmetic/load_plus_arithmetic"
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(50)
          while (c.io.registerFileContents.get(1).peekInt() != 20)
            c.clock.step()
          c.io.registerFileContents.get(1).expect(20)
          while (c.io.registerFileContents.get(2).peekInt() != 1)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(1)
          c.clock.step()
      }
  }

  it should "run load_after_store" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/load_after_store/load_after_store"
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(50)
          while (c.io.registerFileContents.get(2).peekInt() != 10)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(10)
          c.clock.step()
      }
  }

  it should "run enter_c" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/enter_c/enter_c")(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(50)
          while (c.io.registerFileContents.get(2).peekInt() != 5)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(5)
          c.clock.step()
      }
  }

  it should "run calculation_c" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/calculation_c/calculation_c"
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(200)
          while (c.io.registerFileContents.get(2).peekInt() != 18)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(18)
          c.clock.step()
      }
  }

  it should "run loop_c" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/loop_c/loop_c")(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(400)
          while (c.io.registerFileContents.get(2).peekInt() != 30)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(30)
          c.clock.step()
      }
  }

  it should "run loop_c with 4 parallel" in {
    test(
      new B4ProcessorWithMemory("riscv-sample-programs/loop_c/loop_c")(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(400)
          while (c.io.registerFileContents.get(2).peekInt() != 30)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(30)
          c.clock.step()
      }
  }

  it should "run many_load_store" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/many_load_store/many_load_store"
      )(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(100)
          while (c.io.registerFileContents.get(1).peekInt() != 36)
            c.clock.step()
          c.io.registerFileContents.get(1).expect(36)
          c.clock.step()
      }
  }

  it should "run many_load_store with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/many_load_store/many_load_store"
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(100)
          while (c.io.registerFileContents.get(1).peekInt() != 36)
            c.clock.step()
          c.io.registerFileContents.get(1).expect(36)
          c.clock.step()
      }
  }

  it should "run load_store_cross" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/load_store_cross/load_store_cross"
      )(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(50)
          while (c.io.registerFileContents.get(1).peekInt() != 101)
            c.clock.step()
          c.io.registerFileContents.get(1).expect(101)

          while (c.io.registerFileContents.get(2).peekInt() != 201)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(201)
          c.clock.step()
      }
  }

  it should "run load_store_cross with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
        "riscv-sample-programs/load_store_cross/load_store_cross"
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
        c =>
          c.clock.setTimeout(50)
          while (c.io.registerFileContents.get(1).peekInt() != 101)
            c.clock.step()
          c.io.registerFileContents.get(1).expect(101)

          while (c.io.registerFileContents.get(2).peekInt() != 201)
            c.clock.step()
          c.io.registerFileContents.get(2).expect(201)
          c.clock.step()
      }
  }
}
