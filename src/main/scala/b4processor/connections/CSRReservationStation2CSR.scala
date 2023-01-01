package b4processor.connections

import b4processor.Parameters
import b4processor.modules.csr.CSRAccessType
import b4processor.utils.Tag
import chisel3._

class CSRReservationStation2CSR(implicit params: Parameters) extends Bundle {
  val address = UInt(12.W)
  val value = UInt(64.W)
  val destinationTag = new Tag
  val csrAccessType = new CSRAccessType.Type()
}
