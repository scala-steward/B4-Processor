package b4processor.modules.memory

import b4processor.Parameters
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class InstructionMemoryTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Instruction Memory"

  implicit val defaultParams = Parameters()

  it should "pass values at address" in {
    test(new InstructionMemory((0 until 100).map(_.U(8.W)))) { c =>
      c.io.address.poke(0)
      c.io.output(0).expect("x03020100".U)
      c.io.output(1).expect("x07060504".U)

      c.io.address.poke(8)
      c.io.output(0).expect("x0B0A0908".U)
      c.io.output(1).expect("x0F0E0D0C".U)
    }
  }

}
