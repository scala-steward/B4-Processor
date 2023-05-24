package b4processor.utils

import chisel3._

object SignExtendTo64 {
  def signExtendTo64(s: SInt): SInt = {
    val w = Wire(SInt(64.W))
    w := s
    w
  }
}