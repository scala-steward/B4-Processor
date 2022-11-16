package b4processor.connections

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._
import chisel3.util._

/** LSQとリオーダバッファをつなぐ
  */
class LoadStoreQueue2ReorderBuffer(implicit params: Parameters) extends Bundle {
  val destinationTag =
    Vec(params.maxRegisterFileCommitCount, new Tag)
  val valid = Vec(params.maxRegisterFileCommitCount, Bool())
}
