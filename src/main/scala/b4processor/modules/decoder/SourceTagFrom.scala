package b4processor.modules.decoder

import chisel3._
import chisel3.experimental.ChiselEnum

object SourceTagFrom extends ChiselEnum {
  val ReorderBuffer = Value
  val BeforeDecoder = Value
}
