package b4processor.connections


import b4processor.Parameters
import chisel3._

/**
 * デコーダ同士をつなぐ
 *
 * @param params パラメータ
 */
class Decoder2NextDecoder(implicit params: Parameters) extends Bundle {
  val valid = Bool()
  val destinationTag = UInt(5.W)
  val destinationRegister = UInt(params.tagWidth.W)
}
