package b4processor.modules.executor

import b4processor.Parameters
import b4processor.utils.{ALUValue, FetchValue, LSQValue, ReservationValue}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ExecutorWrapper(implicit params: Parameters) extends Executor {

  def setALU(values: ReservationValue): Unit = {
    val reservationstation = this.io.reservationstation
    reservationstation.valid.poke(values.valid)
    reservationstation.bits.destinationTag.poke(values.destinationTag)
    reservationstation.bits.value1.poke(values.value1)
    reservationstation.bits.value2.poke(values.value2)
    reservationstation.bits.function3.poke(values.function3)
    reservationstation.bits.immediateOrFunction7.poke(values.immediateOrFunction7)
    reservationstation.bits.opcode.poke(values.opcode)
    reservationstation.bits.programCounter.poke(values.programCounter)
  }

  def expectout(values: Option[ALUValue]): Unit = {
    val out = this.io.out
    out.valid.expect(values.isDefined)
    if (values.isDefined) {
      out.destinationTag.expect(values.get.destinationTag)
      out.value.expect(values.get.value)
    }
  }

  def expectLSQ(values: LSQValue): Unit = {
    val loadstorequeue = this.io.loadStoreQueue
    loadstorequeue.destinationTag.expect(values.destinationTag)
    loadstorequeue.value.expect(values.value)
    loadstorequeue.valid.expect(values.valid)
    loadstorequeue.programCounter.expect(values.programCounter)
  }

  def expectFetch(values: FetchValue): Unit = {
    val fetch = this.io.fetch
    fetch.valid.expect(values.valid)
    fetch.programCounter.expect(values.programCounter)
  }
}

class ExecutorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Executor"

  implicit val defaultParams = Parameters(runParallel = 1)

  it should "lui" in {
    test(new ExecutorWrapper) { c =>
      // rs1 = 40, rs2 = 16
      c.setALU(values = ReservationValue(valid = true, destinationTag = 10, value1 = 40, value2 = 16,
        function3 = 0, immediateOrFunction7 = 0, opcode = 55, programCounter = 100))

      c.expectout(values = Some(ALUValue(destinationTag = 10, value = 16)))

      c.expectLSQ(values = LSQValue(destinationTag = 10, 16,
        valid = true, programCounter = 100))

      c.expectFetch(values = FetchValue(valid = false, programCounter = 100))
    }
  }

  it should "auipc" in {
    test(new ExecutorWrapper) { c =>
      // rs1 = 40, rs2 = 16
      c.setALU(values = ReservationValue(valid = true, destinationTag = 10, value1 = 40, value2 = 16,
        function3 = 0, immediateOrFunction7 = 0, opcode = 55, programCounter = 100))

      c.expectout(values = Some(ALUValue(destinationTag = 10, value = 16)))

      c.expectLSQ(values = LSQValue(destinationTag = 10, value = 16,
        valid = true, programCounter = 100))

      c.expectFetch(values = FetchValue(valid = false, programCounter = 100))
    }
  }

  it should "jal" in {
    test(new ExecutorWrapper) { c =>
      // rs1 = 40, rs2 = 16
      c.setALU(values = ReservationValue(valid = true, destinationTag = 10, value1 = 40, value2 = 16,
        function3 = 0, immediateOrFunction7 = 0, opcode = 111, programCounter = 100))

      c.expectout(values = Some(ALUValue(destinationTag = 10, value = 104)))

      c.expectLSQ(values = LSQValue(destinationTag = 10, value = 104,
        valid = true, programCounter = 100))

      c.expectFetch(values = FetchValue(valid = true, programCounter = 116))
    }
  }

  it should "jalr" in {
    test(new ExecutorWrapper) { c =>
      //
      c.setALU(values = ReservationValue(valid = true, destinationTag = 10, value1 = 40, value2 = 16,
        function3 = 0, immediateOrFunction7 = 0, opcode = 103, programCounter = 100))

      c.expectout(values = Some(ALUValue(destinationTag = 10, value = 104)))

      c.expectLSQ(values = LSQValue(destinationTag = 10, value = 104,
        valid = true, programCounter = 100))

      c.expectFetch(values = FetchValue(valid = true, programCounter = 56))
    }
  }


  it should "add" in {
    test(new ExecutorWrapper) { c =>
      // rs1 = 40, rs2 = 30
      c.setALU(values = ReservationValue(valid = true, destinationTag = 10, value1 = 40, value2 = 30,
        function3 = 0, immediateOrFunction7 = 0, opcode = 51, programCounter = 100))

      c.expectout(values = Some(ALUValue(destinationTag = 10, value = 70)))

      c.expectLSQ(values = LSQValue(destinationTag = 10, value = 70,
        valid = true, programCounter = 100))

      c.expectFetch(values = FetchValue(valid = false, programCounter = 100))
    }
  }

  it should "sub" in {
    test(new ExecutorWrapper) { c =>
      // rs1 = 40, rs2 = 30
      c.setALU(values = ReservationValue(valid = true, destinationTag = 10, value1 = 40, value2 = 30,
        function3 = 0, immediateOrFunction7 = 32, opcode = 51, programCounter = 100))

      c.expectout(values = Some(ALUValue(destinationTag = 10, value = 10)))

      c.expectLSQ(values = LSQValue(destinationTag = 10, value = 10,
        valid = true, programCounter = 100))

      c.expectFetch(values = FetchValue(valid = false, programCounter = 100))
    }
  }
}

