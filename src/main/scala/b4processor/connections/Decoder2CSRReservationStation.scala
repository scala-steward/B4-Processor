package b4processor.connections

import b4processor.Parameters
import b4processor.modules.csr.CSRAccessType
import b4processor.utils.Tag
import chisel3._

class Decoder2CSRReservationStation(implicit params: Parameters)
    extends Bundle {
  val sourceTag = new Tag
  val destinationTag = new Tag
  val value = UInt(64.W)
  val ready = Bool()
  val address = UInt(12.W)
  val csrAccessType = new CSRAccessType.Type()
}