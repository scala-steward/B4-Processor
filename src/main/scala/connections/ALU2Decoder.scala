package connections

import chisel3._
import chisel3.util.ReadyValidIO
import consts.Constants.TAG_WIDTH

/**
 * ALUからデコーダへバイパスされたデータを送る
 */
class ALU2Decoder extends ReadyValidIO(new Bundle {
  val destinationTag = UInt(TAG_WIDTH.W)
  val value = UInt(64.W)
})
