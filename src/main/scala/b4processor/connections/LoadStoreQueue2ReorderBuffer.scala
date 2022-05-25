package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * LSQとリオーダバッファをつなぐ
 */
class LoadStoreQueue2ReorderBuffer(implicit params: Parameters) extends Bundle {
  val programCounter = Vec(params.maxRegisterFileCommitCount, SInt(64.W))
  val valid = Vec(params.maxRegisterFileCommitCount, Bool())
}
