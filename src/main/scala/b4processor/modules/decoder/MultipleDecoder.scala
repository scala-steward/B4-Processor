package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.connections.Fetch2Decoder
import chisel3._

/**
 * 複数のデコーダをつなげるモジュール
 *
 * @param params パラメータ
 */
class MultipleDecoder(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val instructions = Vec(params.numberOfDecoders, new Fetch2Decoder)
  })

  val decoders = (0 until params.numberOfDecoders).map(i => Module(new Decoder(i)))

  for (i <- 1 until params.numberOfDecoders) {
    decoders(i).io.decodersBefore <> decoders(i - 1).io.decodersAfter
  }

  for (i <- 0 until params.numberOfDecoders) {
    decoders(i).io.instructionFetch <> io.instructions(i)
  }
}
