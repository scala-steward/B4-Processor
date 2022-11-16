package b4processor.modules.memory

import b4processor.Parameters
import b4processor.modules.cache.DataMemoryBuffer
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth
import b4processor.utils.Tag

class DataMemoryBufferTestWrapper(implicit params: Parameters)
    extends DataMemoryBuffer {}

class DataMemoryBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Data Memory Buffer"

  implicit val params = Parameters(maxRegisterFileCommitCount = 2, tagWidth = 5)

  it should "enqueue and dequeue" in {
    test(new DataMemoryBufferTestWrapper)
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.io.dataIn(0).valid.poke(true)
        c.io.dataIn(0).bits.address.poke(4)
        c.io.dataIn(0).bits.tag.poke(Tag(10))
        c.io.dataIn(0).bits.data.poke(123)
        c.io.dataIn(0).bits.accessInfo.accessType.poke(Load)
        c.io.dataReadRequest.ready.poke(true)
        c.io.dataWriteRequest.ready.poke(true)

        c.io.dataIn(1).valid.poke(true)
        c.io.dataIn(1).bits.address.poke(8)
        c.io.dataIn(1).bits.tag.poke(Tag(11))
        c.io.dataIn(1).bits.data.poke(1234)
        c.io.dataIn(1).bits.accessInfo.accessType.poke(Store)
        c.io.dataIn(1).bits.accessInfo.accessWidth.poke(MemoryAccessWidth.Word)
        c.clock.step(1)

        c.io.dataIn(0).valid.poke(false)
        c.io.dataIn(1).valid.poke(false)

        c.io.dataReadRequest.valid.expect(true)
        c.io.dataWriteRequest.valid.expect(false)
        c.io.dataReadRequest.bits.address.expect(4)
        c.io.dataReadRequest.bits.outputTag.id.expect(10)
        c.clock.step(1)

        c.io.dataReadRequest.valid.expect(false)
        c.io.dataWriteRequest.valid.expect(true)
        c.io.dataWriteRequest.bits.address.expect(8)
        c.io.dataWriteRequest.bits.outputTag.id.expect(11)
        c.io.dataWriteRequest.bits.data.expect(1234)
        c.clock.step(4)
      }
  }
}
