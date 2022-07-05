package b4processor.modules.memory

import b4processor.Parameters
import chisel3._
import chisel3.util._

class DataMemoryBufferEntry(implicit params: Parameters) extends Bundle {

  /** アドレス値 */
  val address = SInt(64.W)

  /** 命令を識別するためのタグ(Destination Tag) */
  val tag = UInt(params.tagWidth.W)

  /** ストアデータ */
  val data = UInt(64.W)

  /** オペコード */
  val isLoad = Bool()

  /** function3 */
  val function3 = UInt(3.W)
}

object DataMemoryBufferEntry {
  def validEntry(
    address: SInt,
    tag: UInt,
    data: UInt,
    isLoad: Bool,
    function3: UInt
  )(implicit params: Parameters): DataMemoryBufferEntry = {
    val entry = DataMemoryBufferEntry.default
    entry.address := address
    entry.tag := tag
    entry.data := data
    entry.isLoad := isLoad
    entry.function3 := function3

    entry
  }

  def default(implicit params: Parameters): DataMemoryBufferEntry = {
    val entry = Wire(new DataMemoryBufferEntry)
    entry.address := 0.S
    entry.tag := 0.U
    entry.data := 0.U
    entry.isLoad := false.B
    entry.function3 := 0.U

    entry
  }
}
