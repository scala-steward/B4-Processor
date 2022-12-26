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

  private val queue = Module(
    new Queue(t, pow(2, width).toInt, useSyncReadMem = true)
  )
  queue.io.enq <> input
  output <> queue.io.deq
  full := !queue.io.enq.ready
  empty := !queue.io.deq.valid
}

object FIFO extends App {
  (new ChiselStage).emitVerilog(new FIFO(8)(new Bundle {
    val a = UInt(32.W)
  }))
}
