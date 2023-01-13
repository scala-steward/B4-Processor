package b4processor.z_B4ProcessorTests

import b4processor.Parameters
import b4processor.utils.B4ProcessorWithMemory
import chiseltest._
import chiseltest.internal.CachingAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import treadle.RandomizeAtStartupAnnotation

import java.io.FileWriter

class B4ProcessorParameterTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor with many parameters"
  implicit val defaultParams = Parameters(debug = true)

  for (threads <- Seq(1, 2, 4))
    for (decoderPerThread <- Seq(1, 3))
      for (maxCommitCount <- Seq(1, 3))
        for (tagWidth <- Seq(2, 5))
          for (lsqWidth <- Seq(2, 5)) {
            val title =
              s"run fibonacci_c threads=${threads} decoders=${decoderPerThread} maxCommitCount=${maxCommitCount} tagWidth=${tagWidth} lsqWidth=${lsqWidth}"
            it should title in {
              test(
                new B4ProcessorWithMemory()(
                  defaultParams.copy(
                    threads = threads,
                    decoderPerThread = decoderPerThread,
                    tagWidth = tagWidth,
                    maxRegisterFileCommitCount = maxCommitCount,
                    loadStoreQueueIndexWidth = lsqWidth
                  )
                )
              )
                .withAnnotations(
                  Seq(
                    WriteVcdAnnotation,
                    IcarusBackendAnnotation,
                    CachingAnnotation,
                    RandomizeAtStartupAnnotation
                  )
                ) { c =>
                  c.initialize("programs/riscv-sample-programs/fibonacci_c")
                  for (t <- 0 until threads)
                    c.checkForRegister(3, 21, 1500, t)
                  println(title)
                  val fw = new FileWriter("stats.jsonl", true)
                  val ipcs = (0 until threads)
                    .map(t =>
                      (c.io.registerFileContents
                        .get(t)(6)
                        .peekInt()
                        .toDouble / c.io.registerFileContents
                        .get(t)(5)
                        .peekInt()
                        .toDouble)
                    )
                    .map(_.toString)
                    .reduce((a, b) => a + "," + b)
                  try {
                    fw.write(
                      s"{\"threads\":$threads, \"decoders\":${decoderPerThread}, \"maxCommitCount\":${maxCommitCount}, \"tagWidth\":${tagWidth}, \"lsqWidth\":${lsqWidth}, \"ipc\":[$ipcs]}\n"
                    )
                  } finally fw.close()

                }
            }
          }
}
