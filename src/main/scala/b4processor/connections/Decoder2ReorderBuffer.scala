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

  class SourceRegister extends Bundle {
    val sourceRegister = Output(UInt(5.W))
    val matchingTag = Flipped(DecoupledIO(UInt(TAG_WIDTH.W)))
    val value = Flipped(DecoupledIO(UInt(64.W)))
  }

  class DestinationRegister extends Bundle {
    val destinationRegister = DecoupledIO(UInt(5.W))
    val destinationTag = Input(UInt(TAG_WIDTH.W))
  }
}
