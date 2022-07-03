package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{Fetch2FetchBuffer, FetchBuffer2Decoder}
import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor

class FetchBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(params.runParallel, new FetchBuffer2Decoder)
    val fetch = Flipped(new Fetch2FetchBuffer)
  })

  val buffer = RegInit(
    VecInit(
      Seq.fill(params.runParallel)
      (new Bundle {
        val instruction = UInt(32.W)
        val programCounter = SInt(64.W)
        val valid = Bool()
      }.Lit(_.valid -> false.B, _.programCounter -> 0.S, _.instruction -> 0.U))
    )
  )

  io.fetch.ready := !Cat(buffer.map(_.valid)).orR || Cat(io.decoders.map(_.ready)).andR


  for (i <- 0 until params.runParallel) {
    when(io.fetch.ready) {
      buffer(i).valid := io.fetch.decoder(i).valid
      buffer(i).instruction := io.fetch.decoder(i).bits.instruction
      buffer(i).programCounter := io.fetch.decoder(i).bits.programCounter
    }

    io.decoders(i).valid := buffer(i).valid
    io.decoders(i).bits.instruction := buffer(i).instruction
    io.decoders(i).bits.programCounter := buffer(i).programCounter
  }

}
