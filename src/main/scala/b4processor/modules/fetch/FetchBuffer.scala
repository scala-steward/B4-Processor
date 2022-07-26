package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{Fetch2FetchBuffer, FetchBuffer2Decoder}
import chisel3._
import chisel3.stage.ChiselStage

import scala.math.pow

class FetchBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.runParallel, new FetchBuffer2Decoder)
    val fetch = Flipped(new Fetch2FetchBuffer)
  })

  val buffer = Reg(Vec(pow(2, params.runParallel + 2).toInt, new BufferEntry))

  val head = RegInit(0.U((params.runParallel + 2).W))
  val tail = RegInit(0.U((params.runParallel + 2).W))

  {
    var nextHead = head
    for (d <- io.fetch.decoder) {
      val indexOk = nextHead + 1.U =/= tail
      d.ready := indexOk
      val valid = d.valid && indexOk
      when(valid) {
        buffer(nextHead) := BufferEntry.validEntry(
          d.bits.instruction,
          d.bits.programCounter
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
      val valid = d.ready && indexOk
      d.bits.instruction := 0.U
      d.bits.programCounter := 0.S
      when(valid) {
        d.bits.instruction := buffer(nextTail).instruction
        d.bits.programCounter := buffer(nextTail).programCounter
      }
      nextTail = Mux(valid, nextTail + 1.U, nextTail)
    }
    tail := nextTail
  }

}

sealed class BufferEntry extends Bundle {
  val instruction = UInt(32.W)
  val programCounter = SInt(32.W)
}

object BufferEntry extends App {
  implicit val params = Parameters(tagWidth = 2, runParallel = 1)
  (new ChiselStage).emitVerilog(
    new FetchBuffer(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )

  def default(): BufferEntry = {
    val w = Wire(new BufferEntry)
    w.instruction := 0.U
    w.programCounter := 0.S
    w
  }

  def validEntry(instruction: UInt, programCounter: SInt): BufferEntry = {
    val w = Wire(new BufferEntry)
    w.instruction := instruction
    w.programCounter := programCounter
    w
  }
}
