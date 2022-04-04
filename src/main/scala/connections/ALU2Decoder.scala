package connections

import chisel3._
import consts.Constants.TAG_WIDTH

class ALU2Decoder extends Bundle {
  val dtag = UInt(TAG_WIDTH.W)
  val value = UInt(64.W)
}
