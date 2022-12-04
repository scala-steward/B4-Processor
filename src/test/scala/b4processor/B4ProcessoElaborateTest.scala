package b4processor

import b4processor.utils.{B4ProcessorWithMemory, InstructionUtil}
import chisel3._
import chisel3.stage.ChiselStage
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessoElaborateTest extends AnyFlatSpec with ChiselScalatestTester {

  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams = Parameters(debug = true, tagWidth = 4)

  behavior of "B4Processor connections"
  // コンパイルが通ることを確認（信号をつなぎきれていないとエラーになる）
  for (threads <- 1 to 2)
    for (decoderPerThread <- 1 to 3)
      for (maxCommitCount <- 1 to 3)
        for (tagWidth <- 2 to 3)
          it should s"elaborate threads=${threads} decoder=${decoderPerThread} maxCommitCount=${maxCommitCount} tagWidth=${tagWidth}" in {
            (new ChiselStage).emitFirrtl(
              new B4ProcessorWithMemory()(
                defaultParams.copy(
                  threads = threads,
                  decoderPerThread = decoderPerThread,
                  maxRegisterFileCommitCount = maxCommitCount,
                  tagWidth = tagWidth
                )
              )
            )
          }
}
