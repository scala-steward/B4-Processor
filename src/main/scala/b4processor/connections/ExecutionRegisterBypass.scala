package b4processor.connections

import b4processor.Constants.TAG_WIDTH
import chisel3._
import chisel3.util.ReadyValidIO

/**
 * ALUからデコーダへバイパスされたデータを送る
 */
class ExecutionRegisterBypass extends ReadyValidIO(new Bundle {
  val destinationTag = UInt(TAG_WIDTH.W)
  val value = UInt(64.W)
})
