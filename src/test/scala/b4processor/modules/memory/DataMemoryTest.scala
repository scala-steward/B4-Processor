package b4processor.modules.memory

import b4processor.Parameters
import b4processor.utils.DataMemoryValue
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class DataMemoryTestWrapper(implicit params: Parameters) extends DataMemory {}

class DataMemoryTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Data Memory"

  implicit val params = Parameters()

  it should "store and load a value" in {
    test(new DataMemoryTestWrapper) { c =>
      c.io.dataIn.bits.address.poke(0)
      c.io.dataIn.bits.data.poke(123)
      c.io.dataIn.bits.tag.poke(0)
      c.io.dataIn.bits.function3.poke("b011".U)
      c.io.dataIn.bits.opcode.poke("b0000011".U)
      c.io.dataIn.valid.poke(true)

      c.clock.step()

      c.io.dataIn.bits.address.poke(0)
      c.io.dataIn.bits.data.poke(123)
      c.io.dataIn.bits.tag.poke(0)
      c.io.dataIn.bits.function3.poke("b011".U)
      c.io.dataIn.bits.opcode.poke("b0000010".U)
      c.io.dataOut.valid.expect(true)
      c.io.dataOut.bits.data.expect(123)
    }
  }
}