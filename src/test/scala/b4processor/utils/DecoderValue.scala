package b4processor.utils

case class DecoderValue(
  valid: Boolean = false,
  source1: Int = 0,
  source2: Int = 0,
  destination: Int = 0,
  programCounter: Int = 0,
  isPrediction: Boolean = false
)
