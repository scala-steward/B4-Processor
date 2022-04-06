package b4processor.decoder

import b4processor.modules.decoder.MultipleDecoder
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MultipleDecoderWrapper(number_of_decoders: Int, number_of_alus: Int) extends MultipleDecoder(number_of_decoders, number_of_alus) {

}

class MultipleDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "multiple decoders"

  it should "understand two instructions" in {
    //    test(new MultipleDecoderWrapper(2)) {c =>
    //      c.io.instructions(0).program_counter := 10.U
    //      c.io.instructions(0).instruction
    //      c.io.instructions(0).
    //    }
  }
}
