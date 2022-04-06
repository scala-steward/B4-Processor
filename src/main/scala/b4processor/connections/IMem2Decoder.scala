package b4processor.connections

import chisel3._
import chisel3.util.ReadyValidIO

/**
 * 命令とデコーダをつなぐ
 */
class IMem2Decoder extends ReadyValidIO(new Bundle {
  val instruction = UInt(64.W)
  val program_counter = UInt(64.W)
})
