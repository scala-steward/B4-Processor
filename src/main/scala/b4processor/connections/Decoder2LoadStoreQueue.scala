package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * デコーダとLSQをつなぐ
 *
 * @param params パラメータ
 */
class Decoder2LoadStoreQueue(implicit params: Parameters) extends  Bundle {
  val opcode = Output(UInt(7.W))
  val stag2 = Output(UInt(params.tagWidth.W))
  val value = DecoupledIO(UInt(64.W))
  val programCounter = Output(UInt((64.W)))
}