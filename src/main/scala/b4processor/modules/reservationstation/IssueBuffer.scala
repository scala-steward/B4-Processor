package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.connections.ReservationStation2Executor
import b4processor.utils.MMArbiter
import chisel3._
import chisel3.experimental.prefix
import chisel3.util._
import _root_.circt.stage.ChiselStage

import scala.math.pow

class IssueBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reservationStations = Vec(
      params.threads,
      Vec(
        params.decoderPerThread,
        Flipped(Decoupled(new ReservationStation2Executor))
      )
    )
    val executors =
      Vec(params.executors, Decoupled(new ReservationStation2Executor()))
  })

  private val widthPerThread = log2Up(params.decoderPerThread * 2)

  val heads = RegInit(VecInit(Seq.fill(params.threads)(0.U(widthPerThread.W))))
  val buffers = Reg(
    Vec(
      params.threads,
      Vec(pow(2, widthPerThread).toInt, new ReservationStation2Executor)
    )
  )
  val buffers_valid = RegInit(
    VecInit(
      Seq.fill(params.threads)(
        VecInit(Seq.fill(pow(2, widthPerThread).toInt)(false.B))
      )
    )
  )

  for (t <- 0 until params.threads) {
    prefix(s"thread$t") {
      val head = heads(t)
      val buffer = buffers(t)
      val buffer_valid = buffers_valid(t)

      val input = io.reservationStations(t)
      var nextHead = head;
      for (i <- input) {
        i.ready := false.B
        when(!buffer_valid(nextHead) && i.valid) {
          buffer_valid(nextHead) := true.B
          buffer(nextHead) := i.bits
          i.ready := true.B
        }
        nextHead =
          Mux(!buffer_valid(nextHead) && i.valid, nextHead + 1.U, nextHead)
      }
      head := nextHead
    }
  }

  val arbiter = Module(
    new MMArbiter(
      new ReservationStation2Executor,
      params.threads * pow(2, widthPerThread).toInt,
      params.executors
    )
  )

  for (t <- 0 until params.threads) {
    for (b <- 0 until pow(2, widthPerThread).toInt) {
      val buf = buffers(t)(b)
      val valid = buffers_valid(t)(b)
      val arb = arbiter.io.input(t + b * params.threads)
      arb.bits := buf
      arb.valid := valid
      when(valid && arb.ready) {
        valid := false.B
      }
    }
  }

  for (e <- 0 until params.executors) {
    io.executors(e) <> arbiter.io.output(e)
  }
}

object IssueBuffer extends App {
  implicit val params = Parameters()
  ChiselStage.emitSystemVerilogFile(new IssueBuffer)
}
