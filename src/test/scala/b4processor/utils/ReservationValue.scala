package b4processor.utils

case class ReservationValue(valid: Boolean,
                            destinationTag: Int,
                            value1: BigInt,
                            value2: BigInt,
                            function3: Int,
                            immediateOrFunction7: Int,
                            opcode: Int,
                            programCounter: BigInt)
