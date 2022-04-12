package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util.ReadyValidIO

/**
 * ALUからデコーダへバイパスされたデータを送る
 * @param params パラメータ
 */
class ExecutionRegisterBypass(implicit val params:Parameters) extends ReadyValidIO(new Bundle {
  val destinationTag = UInt(params.tagWidth.W)
  val value = UInt(64.W)
})
