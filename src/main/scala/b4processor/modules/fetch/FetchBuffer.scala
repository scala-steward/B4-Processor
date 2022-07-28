package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{Fetch2FetchBuffer, FetchBuffer2Decoder}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

class FetchBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.runParallel, new FetchBuffer2Decoder)
    val fetch = Flipped(new Fetch2FetchBuffer)
  })

  private val width = log2Up(params.runParallel) + 2
  val buffer = Reg(Vec(pow(2, width).toInt, new BufferEntry))

  val head = RegInit(0.U(width.W))
  val tail = RegInit(0.U(width.W))

  when(io.fetch.flush) {
    tail := head
    for (d <- io.decoders) {
      d.valid := false.B
      d.bits := DontCare
    }
    for (f <- io.fetch.decoder)
      f.ready := false.B
  }.otherwise {
    {
      var nextHead = head
      for (d <- io.fetch.decoder) {
        val indexOk = nextHead + 1.U =/= tail
        d.ready := indexOk
        val valid = d.valid && indexOk
        when(valid) {
          buffer(nextHead) := BufferEntry.validEntry(
            d.bits.instruction,
            d.bits.programCounter,
            d.bits.branchID,
            d.bits.isBranch
          )
        }
        nextHead = Mux(valid, nextHead + 1.U, nextHead)
      }
      head := nextHead
    }

    {
      var nextTail = tail
      for (d <- io.decoders) {
        val indexOk = nextTail =/= head
        d.valid := indexOk
        d.bits := DontCare
        when(d.valid) {
          val entry = buffer(nextTail)
          d.bits.instruction := entry.instruction
          d.bits.programCounter := entry.programCounter
          d.bits.branchID := entry.branchID
          d.bits.isBranch := entry.isBranch
        }
        nextTail = Mux(d.ready && indexOk, nextTail + 1.U, nextTail)
      }
      tail := nextTail
    }
  }

}

sealed class BufferEntry(implicit params: Parameters) extends Bundle {
  val instruction = UInt(32.W)
  val programCounter = SInt(32.W)
  val branchID = UInt(params.branchBufferSize.W)
  val isBranch = Bool()
}

object BufferEntry extends App {
  implicit val params = Parameters(tagWidth = 2, runParallel = 1)
  (new ChiselStage).emitVerilog(
    new FetchBuffer(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )

  def validEntry(
    instruction: UInt,
    programCounter: SInt,
    branchID: UInt,
    isBranch: Bool
  )(implicit params: Parameters): BufferEntry = {
    val w = Wire(new BufferEntry)
    w.instruction := instruction
    w.programCounter := programCounter
    w.branchID := branchID
    w.isBranch := isBranch
    w
  }
}
