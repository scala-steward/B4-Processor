package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * デコーダとLSQをつなぐ
 *
 * @param params パラメータ
 */
class Decoder2LoadStoreQueue(implicit params: Parameters) extends ReadyValidIO(new Bundle {
  val opcode = UInt(7.W)
  val stag2 = UInt(params.tagWidth.W)
  val value = UInt(64.W)
  val programCounter = UInt(64.W)
})