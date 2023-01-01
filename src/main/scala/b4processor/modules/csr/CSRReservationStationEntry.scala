package b4processor.modules.csr

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._

class CSRReservationStationEntry(implicit params: Parameters) extends Bundle {
  val sourceTag = new Tag
  val value = UInt(64.W)
  val ready = Bool()
  val destinationTag = new Tag
  val address = UInt(12.W)
  val csrAccessType = new CSRAccessType.Type()
}
