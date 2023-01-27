package b4processor.modules.csr

import chisel3._
import chisel3.experimental.ChiselEnum

object CSRAccessType extends ChiselEnum {
  val ReadWrite, ReadSet, ReadClear = Value
}
