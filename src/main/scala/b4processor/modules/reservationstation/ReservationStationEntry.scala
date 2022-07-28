package b4processor.modules.reservationstation

import b4processor.Parameters
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor

/** デコーダとリザベーションステーションをつなぐ
  *
  * @param params
  *   パラメータ
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
  val programCounter = SInt(64.W)
  val branchID = UInt(params.branchBufferSize.W)
  val valid = Bool()
}

object ReservationStationEntry {
  def default(implicit params: Parameters): ReservationStationEntry =
    (new ReservationStationEntry).Lit(_.valid -> false.B)
}
