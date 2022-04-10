package b4processor

import org.scalatest.flatspec.AnyFlatSpec

class ConstantsTest extends AnyFlatSpec {
  behavior of "constants"

  it should "have the same value for decoders and alus" in {
    assert(Constants.NUMBER_OF_ALUS == Constants.NUMBER_OF_DECODERS)
  }
}
