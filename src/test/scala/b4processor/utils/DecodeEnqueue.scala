package b4processor.utils

case class DecodeEnqueue(valid: Boolean,
                         stag2: Int,
                         value: Int,
                         opcode: Int,
                         ProgramCounter: Int,
                         function3: Int)
