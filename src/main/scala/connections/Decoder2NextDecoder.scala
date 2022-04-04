package connections

import chisel3._
import consts.Constants.TAG_WIDTH

/**
 * デコーダ同士をつなぐ
 */
class Decoder2NextDecoder extends Bundle {
  val valid = Bool()
  val destinationTag = UInt(5.W)
  val destinationRegister = UInt(TAG_WIDTH.W)
}
