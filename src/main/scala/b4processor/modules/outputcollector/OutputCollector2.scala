package b4processor.modules.outputcollector

import _root_.circt.stage.ChiselStage
import b4processor.Parameters
import b4processor.connections.{CollectedOutput, OutputValue}
import b4processor.utils.{B4RRArbiter, FIFO, MMArbiter, PassthroughBuffer}
import chisel3._
import chisel3.experimental.prefix
import chisel3.util._

class OutputCollector2(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val outputs = Vec(params.threads, new CollectedOutput)
    val executor = Flipped(Vec(params.executors, Irrevocable(new OutputValue)))
    val memoryReadResult = Flipped(Irrevocable(new OutputValue))
    val memoryWriteResult = Flipped(Irrevocable(new OutputValue))
    val amo = Flipped(Irrevocable(new OutputValue))
    val csr = Flipped(Vec(params.threads, Irrevocable(new OutputValue)))
  })

  val inputBuffers = Seq.fill(io.executor.length + 3)(
    Module(new PassthroughBuffer(new OutputValue))
  )

  inputBuffers.head.io.input <> io.memoryReadResult
  inputBuffers(1).io.input <> io.memoryWriteResult
  inputBuffers(2).io.input <> io.amo
  for (idx <- 0 until io.executor.length) {
    inputBuffers(idx + 3).io.input <> io.executor(idx)
  }

  val mmarb = Seq.fill(params.threads)(
    Module(
      new MMArbiter(
        new OutputValue,
        io.executor.length + 4,
        params.parallelOutput
      )
    )
  )

  for (idx <- 0 until io.executor.length + 4) {
    for (t <- 0 until params.threads) {
      mmarb(t).io.input(idx).valid := false.B
      mmarb(t).io.input(idx).bits := 0.U.asTypeOf(new OutputValue)
    }
  }

  for (t <- 0 until params.threads) {
    prefix(s"csr$t") {
      val csr_buf = Module(new PassthroughBuffer(new OutputValue))
      csr_buf.io.input <> io.csr(t)
      mmarb(t).io.input(0) <> csr_buf.io.output
    }
  }

  for ((in, idx) <- inputBuffers.zipWithIndex) {
    in.io.output.ready := false.B
    for (t <- 0 until params.threads) {
      when(in.io.output.bits.tag.threadId === t.U) {
        mmarb(t).io.input(idx + 1) <> in.io.output
      }
    }
  }

  for (t <- 0 until params.threads) {
    for (i <- 0 until params.parallelOutput) {
      io.outputs(t).outputs(i).valid := mmarb(t).io.output(i).valid
      io.outputs(t).outputs(i).bits := mmarb(t).io.output(i).bits
      when(mmarb(t).io.output(i).valid) {
        assert(
          mmarb(t).io.output(i).bits.tag.threadId === t.U,
          s"check for thread $t, output ${i}"
        )
      }
      io.outputs(t).outputs(i).bits.tag.threadId := t.U
      mmarb(t).io.output(i).ready := true.B
    }
    assume(
      io.csr(t).bits.tag.threadId === t.U,
      s"csr outputs should have correct threadid ${t}"
    )
  }

}

object OutputCollector2 extends App {
  implicit val params = Parameters(threads = 2, executors = 4)
  ChiselStage.emitSystemVerilogFile(new OutputCollector2)
}
