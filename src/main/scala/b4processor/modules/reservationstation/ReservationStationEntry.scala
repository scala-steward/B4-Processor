package b4processor.modules.reservationstation

import b4processor.Parameters
import chisel3._

/**
 * デコーダとリザベーションステーションをつなぐ
 *
 * @param params パラメータ
 */
class ReservationStationEntry(implicit params: Parameters) extends Bundle {
  val opcode = UInt(7.W)
  val function3 = UInt(3.W)
  val immediateOrFunction7 = UInt(12.W)
  val sourceTag1 = UInt(params.tagWidth.W)
  val ready1 = Bool()
  val value1 = UInt(64.W)
  val sourceTag2 = UInt(params.tagWidth.W)
  val ready2 = Bool()
  val value2 = UInt(64.W)
  val destinationTag = UInt(params.tagWidth.W)
  val programCounter = UInt(64.W)
  val valid = Bool()
}
