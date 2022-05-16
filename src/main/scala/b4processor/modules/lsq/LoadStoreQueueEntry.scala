package b4processor.modules.lsq

import b4processor.Parameters
import chisel3._

/**
 * LSQのエントリ
 *
 * @param params パラメータ
 */
class LoadStoreQueueEntry(implicit params: Parameters) extends Bundle {
  val opcode = UInt(7.W)
  val Readyaddress = Bool()
  val address = SInt(64.W)
  val Readydata = Bool()
  val tag = UInt(params.tagWidth.W)
  val data = UInt(64.W)
  val programCounter = SInt(64.W)
  val R = Bool()
}
