package b4processor.connections

import b4processor.Parameters
import chisel3._

/**
 * ALUからデコーダへバイパスされたデータを送る
 *
 * @param params パラメータ
 */
class ExecutionRegisterBypass(implicit val params: Parameters) extends Bundle {
  val destinationTag = Output(UInt(params.tagWidth.W))
  val value = Output(UInt(64.W))
  val valid = Output(Bool())
}
