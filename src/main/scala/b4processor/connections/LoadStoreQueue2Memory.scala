package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * LSQとメモリをつなぐ
 */
class LoadStoreQueue2Memory extends ReadyValidIO(new Bundle{
  val address = UInt(64.W)
  val data = UInt(64.W)
})
