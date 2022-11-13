package b4processor

import chiseltest._
import chiseltest.internal.CachingAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorParameterTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor with many parameters"
  implicit val defaultParams = Parameters(debug = true)

  for (runParallel <- Seq(1, 3))
    for (maxCommitCount <- Seq(1, 3))
      for (tagWidth <- Seq(2, 5))
        for (lsqWidth <- Seq(2, 5))
          it should s"run fibonacci runParallel${runParallel} maxCommitCount=${maxCommitCount} tagWidth=${tagWidth} lsqWidth=${lsqWidth}" in {
            test(
              new B4ProcessorWithMemory(
                "riscv-sample-programs/fibonacci_c/fibonacci_c"
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
                Seq(
                  WriteFstAnnotation,
                  VerilatorBackendAnnotation,
                  CachingAnnotation
                )
              ) { c =>
                c.clock.setTimeout(500)
                while (c.io.registerFileContents.get(2).peekInt() == 0)
                  c.clock.step()
                c.io.registerFileContents.get(2).expect(21)
                c.clock.step(10)
              }
          }
}
