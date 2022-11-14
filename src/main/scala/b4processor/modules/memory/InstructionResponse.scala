package b4processor.modules.memory
import chisel3._

class InstructionResponse extends Bundle {
  val inner = UInt(64.W)
}
