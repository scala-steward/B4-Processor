package b4processor.utils

case class ReservationValue(
  valid: Boolean = true,
  destinationTag: Int = 0,
  value1: BigInt = 0,
  value2: BigInt = 0,
  function3: Int = 0,
  immediateOrFunction7: Int = 0,
  opcode: Int = 0,
  programCounter: BigInt = 0
)
