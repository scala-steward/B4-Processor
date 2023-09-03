package b4processor.modules.memory

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessWidth
import b4processor.utils.Tag
import chisel3._

object MemoryReadIntent extends ChiselEnum {
  val Instruction, Data, Amo = Value
}

object MemoryWriteIntent extends ChiselEnum {
  val Data, Amo = Value
}

class MemoryWriteTransaction(implicit params: Parameters) extends Bundle {
  val accessType = new MemoryWriteIntent.Type()
  val outputTag = new Tag()
  val address = UInt(64.W)
  val data = UInt(64.W)
  val mask = UInt(8.W)
}

class MemoryReadTransaction(implicit params: Parameters) extends Bundle {
  val accessType = MemoryReadIntent.Type()
  val address = UInt(64.W)
  val burstLength = UInt(8.W)
  val size = new MemoryAccessWidth.Type()
  val signed = Bool()
  val outputTag = new Tag()
}

object MemoryReadTransaction {
  def ReadToTag(
    address: UInt,
    size: MemoryAccessWidth.Type,
    signed: Bool,
    outputTag: Tag,
  )(implicit params: Parameters): MemoryReadTransaction = {
    val w = Wire(new MemoryReadTransaction)
    w.accessType := MemoryReadIntent.Data
    w.address := address
    w.burstLength := 0.U
    w.size := size
    w.signed := signed
    w.outputTag := outputTag
    w
  }

  def ReadToAmo(address: UInt, size: MemoryAccessWidth.Type, outputTag: Tag)(
    implicit params: Parameters,
  ): MemoryReadTransaction = {
    val w = Wire(new MemoryReadTransaction)
    w.accessType := MemoryReadIntent.Amo
    w.address := address
    w.burstLength := 0.U
    w.size := size
    w.signed := true.B
    w.outputTag := outputTag
    w
  }

  def ReadInstruction(address: UInt, length: Int, threadId: UInt)(implicit
    params: Parameters,
  ): MemoryReadTransaction = {
    val w = Wire(new MemoryReadTransaction)
    w.accessType := MemoryReadIntent.Instruction
    w.address := address
    w.burstLength := (length - 1).U
    w.size := MemoryAccessWidth.DoubleWord
    w.signed := false.B
    w.outputTag := Tag(threadId, 0.U)
    w
  }
}
