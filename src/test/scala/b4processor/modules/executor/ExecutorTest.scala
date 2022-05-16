package b4processor.modules.executor

import b4processor.Parameters
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ExecutorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ExecutorTest"

  implicit val defaultParams = Parameters(numberOfDecoders = 1)

  it should "set a value" in {
    test(new Executor) { c =>
      // add
      c.io.reservationstation.valid.poke(true)
      c.io.reservationstation.bits.destinationTag.poke(10)
      c.io.reservationstation.bits.value1.poke(40)
      c.io.reservationstation.bits.value2.poke(30)
      c.io.reservationstation.bits.function3.poke(7)
      c.io.reservationstation.bits.immediateOrFunction7.poke(0)
      c.io.reservationstation.bits.opcode.poke(51) // R
      c.io.reservationstation.bits.programCounter.poke(100)

      c.clock.step()

      c.io.reorderBuffer.valid.expect(true)
      c.io.reorderBuffer.value.expect(70)
      c.io.reorderBuffer.destinationTag.expect(10)

      c.io.decoders(0).valid.expect(true)
      c.io.decoders(0).value.expect(70)
      c.io.decoders(0).destinationTag.expect(10)

      c.io.loadstorequeue.valid.expect(false)
      c.io.loadstorequeue.value.expect(70)
      c.io.loadstorequeue.destinationTag.expect(10)
      c.io.loadstorequeue.programCounter.expect(100)

      c.io.fetch.valid.expect(false)
      c.io.fetch.programCounter.expect(100)

    }
  }
}
