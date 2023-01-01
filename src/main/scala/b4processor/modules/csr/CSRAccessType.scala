package b4processor.modules.csr

import chisel3.experimental.ChiselEnum

object CSRAccessType extends ChiselEnum {
  val ReadWrite, ReadSet, ReadClear, ReadWriteImmediate, ReadSetImmediate,
    ReadClearImmediate = Value
}
