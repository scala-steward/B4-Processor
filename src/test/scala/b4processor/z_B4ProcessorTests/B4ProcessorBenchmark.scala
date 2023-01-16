package b4processor.z_B4ProcessorTests

import b4processor.Parameters
import b4processor.utils.B4ProcessorWithMemory
import chiseltest._
import chiseltest.internal.CachingAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorBenchmark extends AnyFlatSpec with ChiselScalatestTester {
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = {
    Parameters(
      debug = true,
      threads = 1,
      decoderPerThread = 1,
      tagWidth = 4,
      loadStoreQueueIndexWidth = 2,
      maxRegisterFileCommitCount = 2,
      instructionStart = 0x8000_0000L
    )
  }
  val backendAnnotation = IcarusBackendAnnotation
  val WriteWaveformAnnotation = WriteFstAnnotation

  behavior of s"RISC-V benchmark"

  it should "run dhrystore" in {
    test(new B4ProcessorWithMemory).withAnnotations(
      Seq(
        WriteWaveformAnnotation,
        CachingAnnotation,
        VerilatorBackendAnnotation
      )
    ) { c =>
      c.initialize(
        "programs/riscv-tests/share/riscv-tests/benchmarks/dhrystone"
      )
      c.clock.setTimeout(100000)
      c.clock.step(100000)
    }
  }

}
