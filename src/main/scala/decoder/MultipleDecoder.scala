package decoder

import chisel3._
import connections.IMem2Decoder

/**
 * 複数のデコーダをつなげるモジュール
 *
 * @param number_of_decoders デコーダの数
 * @param number_of_alus     ALUの数
 */
class MultipleDecoder(number_of_decoders: Int, number_of_alus: Int) extends Module {
  val io = IO(new Bundle {
    val instructions = Vec(number_of_decoders, new IMem2Decoder)
  })

  val decoders = (0 until number_of_decoders).map(i => Module(new Decoder(i, number_of_alus)))

  for (i <- 1 until number_of_decoders) {
    decoders(i).io.decodersBefore <> decoders(i - 1).io.decodersAfter
  }

  for (i <- 0 until number_of_decoders) {
    decoders(i).io.imem <> io.instructions(i)
  }
}
