package b4processor.connections

import b4processor.Parameters
import b4processor.modules.decoder.SourceTagInfo
import chisel3._
import chisel3.util._

/**
 * デコーダとLSQをつなぐ
 *
 * @param params パラメータ
 */
class Decoder2LoadStoreQueue(implicit params: Parameters) extends Bundle {
  val opcode = UInt(7.W)
  val function3 = UInt(3.W)
  val stag2 = UInt(params.tagWidth.W)
  val value = UInt(64.W)
  val programCounter = SInt(64.W)
  val dataSign = Bool()
}