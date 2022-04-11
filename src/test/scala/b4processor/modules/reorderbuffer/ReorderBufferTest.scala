package b4processor.modules.reorderbuffer

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class DecoderValue(val valid: Boolean = false,
                   val source1: Int = 0,
                   val source2: Int = 0,
                   val destination: Int = 0)

class RegisterFileValue(val destinationTag: Int = 0,
                        val value: Int = 0)

class ALUValue(val destinationTag: Int = 0, val value: Int = 0)

class ReorderBufferWrapper(numberOfDecoders: Int, numberOfAlus: Int, maxRegisterFileCommitCount: Int)
  extends ReorderBuffer(numberOfDecoders, numberOfAlus, maxRegisterFileCommitCount) {
  def initialize(): Unit = {
    setALU()
    setDecoder()
  }

  def setALU(values: Seq[Option[ALUValue]] = Seq.fill(numberOfAlus)(None)): Unit = {
    for (i <- 0 until numberOfAlus) {
      val alu = this.io.alus(i)
      val v = values(i)
      this.io.alus(i).valid.poke(v.isDefined)
      if (v.isDefined) {
        this.io.alus(i).bits.destinationTag.poke(v.get.destinationTag)
        this.io.alus(i).bits.value.poke(v.get.value)
      }
    }
  }

  def setDecoder(decoderValues: Seq[DecoderValue] = Seq.fill(numberOfDecoders)(new DecoderValue())): Unit = {
    for (i <- 0 until numberOfDecoders) {
      val decoder = this.io.decoders(i)
      val values = decoderValues(i)
      decoder.valid.poke(values.valid)
      decoder.source1.sourceRegister.poke(values.source1)
      decoder.source2.sourceRegister.poke(values.source2)
      decoder.destination.destinationRegister.poke(values.destination)
    }
  }

  def expectDecoder(): Unit = {

  }

  def expectRegisterFile(outputs: Seq[Option[RegisterFileValue]]): Unit = {
    for (i <- 0 until maxRegisterFileCommitCount) {
      this.io.registerFile(i).valid.expect(outputs(i).isDefined)
      if (outputs(i).isDefined) {
        this.io.registerFile(i).bits.destinationRegister.expect(outputs(i).get.destinationTag)
        this.io.registerFile(i).bits.value.expect(outputs(i).get.value)
      }
    }
  }
}

class ReorderBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Reorder Buffer"

  it should "output nothing to register file on first clock" in {
    test(new ReorderBufferWrapper(1, 1, 1)) { c =>
      c.expectRegisterFile(Seq(None))
    }
  }
}
