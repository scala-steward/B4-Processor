package b4processor.modules.memory

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

class DataWriteContent(implicit params: Parameters) extends Bundle {
  val data = UInt(64.W)
  val mask = UInt(8.W)
  val tag = new Tag()
}

class DataReadContent(implicit params: Parameters) extends Bundle {
  val tag = new Tag()
}

class InstructionFetchContent(implicit params: Parameters) extends Bundle {}

class MemoryTransaction(implicit params: Parameters) extends Bundle {
  val address = UInt(64.W)
  val mode = TransactionMode.Type()

  private val contentSize = Seq(
    new DataReadContent,
    new DataWriteContent,
    new InstructionFetchContent
  ).map(_.getWidth).max
  val content = UInt(contentSize.W)

  def dataWriteContent: DataWriteContent = {
    this.content.asTypeOf(new DataWriteContent)
  }

  def dataReadContent: DataReadContent = {
    this.content.asTypeOf(new DataReadContent)
  }

  def InstructionFetchContent: InstructionFetchContent = {
    this.content.asTypeOf(new InstructionFetchContent)
  }
}

object TransactionMode extends ChiselEnum {
  val InstructionRead, DataRead, DataWrite = Value
}

object MemoryTransaction {
  def default()(implicit params: Parameters): MemoryTransaction = {
    val w = Wire(new MemoryTransaction)
    w.mode := DontCare
    w.address := DontCare
    w.content := DontCare
    w
  }

  def dataWriteTransaction(address: SInt, tag: Tag, data: UInt, mask: UInt)(
    implicit params: Parameters
  ): MemoryTransaction = {
    val w = Wire(new MemoryTransaction)
    w.mode := TransactionMode.DataWrite
    w.address := address
    val c = w.content.asTypeOf(new DataWriteContent)
    c.mask := mask
    c.data := data
    c.tag := tag
    w
  }

  def dataReadTransaction(address: SInt, tag: UInt)(implicit
    params: Parameters
  ): MemoryTransaction = {
    val w = Wire(new MemoryTransaction)
    w.mode := TransactionMode.DataRead
    w.address := address
    val c = w.content.asTypeOf(new DataReadContent)
    c.tag := tag
    w
  }

  def instructionFetchContent(
    address: UInt
  )(implicit params: Parameters): MemoryTransaction = {
    val w = Wire(new MemoryTransaction)
    w.mode := TransactionMode.InstructionRead
    w.address := address
    w.content := DontCare
    w
  }
}
