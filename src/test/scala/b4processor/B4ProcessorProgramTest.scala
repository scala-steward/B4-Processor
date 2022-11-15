package b4processor

import b4processor.utils.{B4ProcessorWithMemory, InstructionUtil}
import chiseltest._
import chisel3._
import chiseltest.internal.CachingAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorProgramTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true, tagWidth = 4)

  // branchプログラムが実行できる
  it should "execute branch with no parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(runParallel = 1)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/branch/branch")
        c.checkForRegister(13, 20, 40)
      }
  }

  // フィボナッチ数列の計算が同時発行数1でできる
  it should "execute fibonacci with no parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(runParallel = 1)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci/fibonacci")
        c.checkForRegister(6, 55, 200)
      }
  }

  // フィボナッチ数列の計算が同時発行数2でできる
  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWithMemory())
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci/fibonacci")
        c.checkForRegister(6, 55, 200)
      }
  }

  // フィボナッチ数列の計算が同時発行数4でできる
  it should "execute fibonacci with 4 parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(runParallel = 4)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci/fibonacci")
        c.checkForRegister(6, 55, 200)
      }
  }

  // call(JALRを使った関数呼び出し)とret(JALRを使った関数からのリターン)がうまく実行できる
  it should "execute call_ret with 2 parallel" in {
    test(new B4ProcessorWithMemory())
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/call_ret/call_ret")
        c.checkForRegister(5, 1, 20)
        c.checkForRegister(6, 2, 20)
        c.checkForRegister(7, 3, 20)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数1で試す
  it should "execute many_add with no parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(runParallel = 1)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数2で試す
  it should "execute many_add with 2 parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(fetchWidth = 8)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数4で試す
  it should "execute many_add with 4 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(runParallel = 4, fetchWidth = 8)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // タグ幅をとても小さくする（すべてのデコーダが使えない）ような状況でもうまく動作する
  it should "execute many_add with 4 parallel with very low tag width" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(runParallel = 4, fetchWidth = 8, tagWidth = 2)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数8で試す
  it should "execute many_add with 8 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          runParallel = 8,
          fetchWidth = 8,
          maxRegisterFileCommitCount = 10
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // アウトオブオーダでできそうな命令を同時発行数4で試す
  it should "execute out_of_order with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams
          .copy(runParallel = 4, fetchWidth = 8, maxRegisterFileCommitCount = 8)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize(
          "riscv-sample-programs/many_add_out_of_order/many_add_out_of_order"
        )
        c.clock.step(30)
        c.io.registerFileContents.get(1).expect(1)
        c.io.registerFileContents.get(2).expect(2)
        c.io.registerFileContents.get(3).expect(3)
        c.io.registerFileContents.get(4).expect(4)
        c.io.registerFileContents.get(5).expect(1)
        c.io.registerFileContents.get(6).expect(2)
        c.io.registerFileContents.get(7).expect(3)
        c.io.registerFileContents.get(8).expect(4)
        c.io.registerFileContents.get(9).expect(1)
        c.io.registerFileContents.get(10).expect(2)
        c.io.registerFileContents.get(11).expect(3)
        c.io.registerFileContents.get(12).expect(4)
      }
  }

  // 単純な値をストアしてロードするプログラム
  it should "run load_store" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(runParallel = 1, maxRegisterFileCommitCount = 1)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c => }
  }

  // 単純な値をストアしてロードするプログラム同時発行数2
  it should "run load_store with 2 parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(runParallel = 2)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_store/load_store")
        c.checkForRegister(3, 10, 120)
        c.io.registerFileContents.get(1).expect(0x4000_0018L)
        c.io.registerFileContents.get(2).expect(10)
        c.io.registerFileContents.get(3).expect(10)
      }
  }

  it should "run fibonacci_c" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci_c/fibonacci_c")
        c.checkForRegister(3, 21, 1000)
      }
  }

  it should "run fibonacci_c with 2 parallel" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          runParallel = 2,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 3
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci_c/fibonacci_c")
        c.checkForRegister(3, 21, 1000)
      }
  }

  it should "run load_plus_arithmetic" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize(
          "riscv-sample-programs/load_plus_arithmetic/load_plus_arithmetic"
        )
        c.checkForRegister(2, 20, 30)
        c.checkForRegister(3, 1, 30)
      }
  }

  it should "run load_after_store" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_after_store/load_after_store")
        c.clock.setTimeout(50)
        while (c.io.registerFileContents.get(2).peekInt() != 10)
          c.clock.step()
        c.io.registerFileContents.get(2).expect(10)
        c.clock.step()
      }
  }

  it should "run enter_c" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/enter_c/enter_c")
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
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/calculation_c/calculation_c")
        c.clock.setTimeout(200)
        while (c.io.registerFileContents.get(2).peekInt() != 18)
          c.clock.step()
        c.io.registerFileContents.get(2).expect(18)
        c.clock.step()
      }
  }

  it should "run loop_c" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/loop_c/loop_c")
        c.clock.setTimeout(400)
        while (c.io.registerFileContents.get(2).peekInt() != 30)
          c.clock.step()
        c.io.registerFileContents.get(2).expect(30)
        c.clock.step()
      }
  }

  it should "run loop_c with 4 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/loop_c/loop_c")
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
      )(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_load_store/many_load_store")
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
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_load_store/many_load_store")
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
      )(
        defaultParams.copy(
          runParallel = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_store_cross/load_store_cross")
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
      )(
        defaultParams.copy(
          runParallel = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_store_cross/load_store_cross")
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
