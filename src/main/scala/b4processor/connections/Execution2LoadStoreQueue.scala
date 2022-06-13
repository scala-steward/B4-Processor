package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * ALUからLSQへデータを送信
 * @param params パラメータ
 */

class Execution2LoadStoreQueue(implicit val params:Parameters) extends Bundle {
  val destinationTag = UInt(params.tagWidth.W)
  val value = UInt(64.W)
  val valid = Bool()
  val ProgramCounter = SInt(64.W)
}
