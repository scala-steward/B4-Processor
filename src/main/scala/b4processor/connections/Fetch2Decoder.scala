package b4processor.connections

import chisel3._
import chisel3.util.ReadyValidIO

/**
 * 命令とデコーダをつなぐ
 */
class Fetch2Decoder extends ReadyValidIO(new Bundle {
  val instruction = UInt(64.W)
  val programCounter = SInt(64.W)
  val isBranch = Bool()
  val predictedBranch = Bool()
})
