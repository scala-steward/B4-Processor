package b4processor.modules.decoder

import b4processor.Parameters
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MultipleDecoderWrapper(params: Parameters = new Parameters) extends MultipleDecoder(params) {

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
