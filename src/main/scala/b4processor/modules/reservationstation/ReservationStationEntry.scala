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
  val valid = Bool()
}

object ReservationStationEntry {
  def default(implicit params: Parameters): ReservationStationEntry =
    (new ReservationStationEntry).Lit(
      _.opcode -> 0.U,
      _.function3 -> 0.U,
      _.immediateOrFunction7 -> 0.U,
      _.sourceTag1 -> 0.U,
      _.ready1 -> false.B,
      _.value1 -> 0.U,
      _.sourceTag2 -> 0.U,
      _.ready2 -> false.B,
      _.value2 -> 0.U,
      _.destinationTag -> 0.U,
      _.programCounter -> 0.S,
      _.valid -> false.B
    )
}
