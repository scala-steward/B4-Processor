package b4processor.modules.memory

import b4processor.Parameters
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class DataMemoryBufferTestWrapper(implicit params: Parameters) extends DataMemoryBuffer {}

class DataMemoryBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Data Memory Buffer"

  implicit val params = Parameters(maxRegisterFileCommitCount = 2, tagWidth = 4)

  it should "enqueue and dequeue" in {
    test(new DataMemoryBufferTestWrapper) { c =>
      c.io.dataIn(0).valid.poke(true)
      c.io.dataIn(0).bits.address.poke(4)
      c.io.dataIn(0).bits.tag.poke(10)
      c.io.dataIn(0).bits.data.poke(123)
      c.io.dataIn(0).bits.isLoad.poke(true)

      c.io.dataIn(1).valid.poke(true)
      c.io.dataIn(1).bits.address.poke(8)
      c.io.dataIn(1).bits.tag.poke(11)
      c.io.dataIn(1).bits.data.poke(1234)
      c.io.dataIn(1).bits.isLoad.poke(false)

      c.clock.step(1)

      c.io.dataOut.ready.poke(true)
      c.io.dataOut.valid.expect(true)
      c.io.dataOut.bits.address.expect(4)
      c.io.dataOut.bits.tag.expect(10)
      c.io.dataOut.bits.data.expect(123)
      c.io.dataOut.bits.isLoad.expect(true)

      c.clock.step(1)

      c.io.dataOut.ready.poke(true)
      c.io.dataOut.valid.expect(true)
      c.io.dataOut.bits.address.expect(8)
      c.io.dataOut.bits.tag.expect(11)
      c.io.dataOut.bits.data.expect(1234)
      c.io.dataOut.bits.isLoad.expect(false)

    }
  }
}
