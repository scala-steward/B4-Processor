package b4processor.connections

import b4processor.Constants.TAG_WIDTH
import chisel3._
import chisel3.util._

/**
 * デコーダとリオーダバッファをつなぐ
 */
class Decoder2ReorderBuffer extends Bundle {
  val source1 = new SourceRegister()
  val source2 = new SourceRegister()
  val destination = new DestinationRegister()
  val programCounter = Output(UInt(64.W))
  // 全体をDecoupledにするとsource1などがすべてOutputにってしまって、間違っているのでこちらに書く
  val ready = Input(Bool())
  val valid = Output(Bool())

  class SourceRegister extends Bundle {
    val sourceRegister = Output(UInt(5.W))
    val matchingTag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = Flipped(DecoupledIO(UInt(64.W)))
  }

  class DestinationRegister extends Bundle {
    val destinationRegister = Output(UInt(5.W))
    val destinationTag = Input(UInt(TAG_WIDTH.W))
  }
}
