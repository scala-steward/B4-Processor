package b4processor

import b4processor.utils.InstructionUtil
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorParameterTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true)

  for (runParallel <- 2 to 4)
    for (maxCommitCount <- 1 to 3)
      for (tagWidth <- 2 to 5)
        for (lsqWidth <- 2 to 5)
          it should s"compile runParallel${runParallel} maxCommitCount=${maxCommitCount} tagWidth=${tagWidth} lsqWidth=${lsqWidth}" in {
            test(
              new B4ProcessorWithMemory(
                InstructionUtil
                  .fromFile32bit(
                    "riscv-sample-programs/fibonacci_c/fibonacci_c.32.hex"
                  )
              )(
                defaultParams.copy(
                  runParallel = runParallel,
                  tagWidth = tagWidth,
                  maxRegisterFileCommitCount = maxCommitCount,
                  loadStoreQueueIndexWidth = lsqWidth
                )
              )
            )
              .withAnnotations(
                Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)
              ) { c =>
                c.clock.setTimeout(500)
                while (c.io.registerFileContents.get(2).peekInt() == 0)
                  c.clock.step()
                c.io.registerFileContents.get(2).expect(21)
                c.clock.step(10)
              }
          }
}
