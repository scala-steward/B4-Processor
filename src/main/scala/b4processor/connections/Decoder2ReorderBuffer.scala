package b4processor.connections

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._
import chisel3.util._

/** デコーダとリオーダバッファをつなぐ
  *
  * @param params
  *   パラメータ
  */
class Decoder2ReorderBuffer(implicit params: Parameters) extends Bundle {
  val source1 = new SourceRegister()
  val source2 = new SourceRegister()
  val destination = new DestinationRegister()
  // 全体をDecoupledにするとsource1などがすべてOutputにってしまって、間違っているのでこちらに書く
  val ready = Input(Bool())
  val valid = Output(Bool())

  class SourceRegister extends Bundle {
    val sourceRegister = Output(UInt(5.W))
    val matchingTag = Flipped(Valid(new Tag))
    val value = Flipped(Valid(UInt(64.W)))
  }

  class DestinationRegister extends Bundle {
    val destinationRegister = Output(UInt(5.W))
    val destinationTag = Input(new Tag)
    val storeSign = Output(Bool())
  }
}
