package connections

import chisel3._
import consts.Constants.TAG_WIDTH

class Decoder2ReservationStationEntry extends Bundle {
  val op_code = UInt(7.W)
  val function3 = UInt(3.W)
  val immediateOrFunction7 = UInt(12.W)
  val sourceTag1 = UInt(TAG_WIDTH.W)
  val ready1 = Bool()
  val value1 = UInt(64.W)
  val sourceTag2 = UInt(TAG_WIDTH.W)
  val ready2 = Bool()
  val value2 = UInt(64.W)
  val destinationTag = UInt(TAG_WIDTH.W)
}
