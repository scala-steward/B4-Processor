package b4processor.connections

import b4processor.Constants.TAG_WIDTH
import chisel3._

/**
 * デコーダ同士をつなぐ
 */
class Decoder2NextDecoder extends Bundle {
  val valid = Bool()
  val destinationTag = UInt(5.W)
  val destinationRegister = UInt(TAG_WIDTH.W)
}
