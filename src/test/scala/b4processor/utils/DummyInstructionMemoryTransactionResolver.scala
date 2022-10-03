package b4processor.utils

import b4processor.Parameters
import b4processor.modules.memory.MemoryTransaction
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class DummyInstructionMemoryTransactionResolver(memoryInit: => Seq[UInt])(
  implicit params: Parameters
) extends Module {
  val io = IO(new Bundle {
    val request = Flipped(Irrevocable(new MemoryTransaction))
    val response = Valid(UInt(64.W))
  })

  val memory = VecInit(memoryInit)

  val waiting :: newRequest :: response :: Nil = Enum(3)

  val state = RegInit(waiting)
  val address = Reg(UInt(64.W))

  io.response.valid := false.B
  io.response.bits := 0.U
  io.request.ready := false.B

  switch(state) {
    is(waiting) {
      when(io.request.valid) {
        state := newRequest
      }
    }
    is(newRequest) {
      io.request.ready := true.B
      val transaction = io.request.bits
      address := transaction.address
      state := response
    }
    is(response) {
      assert(!io.request.valid)
      io.response.valid := true.B
      val data = (0 until 8)
        .map { i => address(63, 3) ## i.U(3.W) }
        .map { addr => memory(addr) }
        .reverseIterator
        .reduce({
          case (a, b) =>
            a ## b
        }: (UInt, UInt) => UInt)
      io.response.bits := data
      state := waiting
    }
  }
}

object DummyInstructionMemoryTransactionResolver extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(
    new DummyInstructionMemoryTransactionResolver(
      InstructionUtil
        .fromStringSeq32bit(Seq("00000013", "00000463", "00000013"))
    )
  )
}
