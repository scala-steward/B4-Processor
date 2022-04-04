package connections

import chisel3._

class IMem2Decoder extends Bundle {
  val instruction = UInt(64.W)
  val program_counter = UInt(64.W)
}
