package b4processor.utils

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

class FIFO[T <: Data](width: Int)(t: T) extends Module {
  val input = IO(Flipped(Irrevocable(t)))
  val output = IO(Irrevocable(t))
  val full = IO(Bool())
  val empty = IO(Bool())

  private val head = RegInit(0.U(width.W))
  private val tail = RegInit(0.U(width.W))
  private val buffer = SyncReadMem(pow(2, width).toInt, t)

  full := head + 1.U === tail
  empty := head === tail

  input.ready := false.B
  when(!full) {
    input.ready := true.B
    when(input.valid) {
      buffer.write(head, input.bits)
      head := head + 1.U
    }
  }

  output.valid := false.B
  private val outputReg = Reg(t)
  output.bits := outputReg
  when(!empty) {
    output.valid := true.B
    when(output.ready) {
      outputReg := buffer.read(tail + 1.U)
      tail := tail + 1.U
    }.otherwise{
      outputReg := buffer.read(tail)
    }
  }.otherwise{
    outputReg := buffer.read(tail)
  }
}

object FIFO extends App {
  (new ChiselStage).emitVerilog(new FIFO(3)(UInt(32.W)))
}
