package b4processor.connections

import chisel3._
import chisel3.util._

/**
 * LSQとリオーダバッファをつなぐ
 */
class LoadStoreQueue2ReorderBuffer extends Bundle {
  val programCounter = Input(UInt(64.W))
  val value = DecoupledIO(UInt(64.W))
  val valid = Output(Bool())
}
