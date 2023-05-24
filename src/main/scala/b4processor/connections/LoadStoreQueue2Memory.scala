package b4processor.connections

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessInfo
import b4processor.utils.{LoadStoreOperation, Tag}
import chisel3._
import chisel3.util._

/** LSQとメモリをつなぐ
  */
class LoadStoreQueue2Memory(implicit params: Parameters) extends Bundle {
  val address = UInt(64.W)
  val tag = new Tag()
  val data = UInt(64.W)
  val operation = LoadStoreOperation()
}
