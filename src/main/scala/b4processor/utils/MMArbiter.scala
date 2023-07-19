package b4processor.utils

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class MMArbiter[T <: Data](t: T, inputs: Int, outputs: Int) extends Module {
  val io = IO(new Bundle {
    val input = Vec(inputs, Flipped(Decoupled(t)))
    val output = Vec(outputs, Decoupled(t))
  })

  // initialize
  for (o <- io.output) {
    o.valid := false.B
    o.bits := 0.U.asTypeOf(t)
  }

  val arbiters = (0 until outputs)
    .map(i => inputs / outputs + (if (inputs % outputs > i) 1 else 0))
    .filter(_ > 0)
    .map(i => Module(new Arbiter(t, i)))

  for ((in, idx) <- io.input.zipWithIndex) {
    arbiters(idx % outputs).io.in(idx / outputs) <> in
  }

  for ((out, arb) <- io.output.zip(arbiters)) {
    out <> arb.io.out
  }
}

object MMArbiter extends App {
  ChiselStage.emitSystemVerilogFile(new MMArbiter(UInt(4.W), 2, 1))
}
