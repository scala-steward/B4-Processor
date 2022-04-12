package b4processor.modules.reorderbuffer

import b4processor.Parameters
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

class ReorderBufferWrapper(implicit params: Parameters) extends ReorderBuffer {
  def initialize(): Unit = {
    setALU()
    setDecoder()
  }


  def setALU(values: Seq[Option[ALUValue]] = Seq.fill(params.numberOfALUs)(None)): Unit = {
    for (i <- 0 until params.numberOfALUs) {
      val alu = this.io.alus(i)
      val v = values(i)
      this.io.alus(i).valid.poke(v.isDefined)
      if (v.isDefined) {
        this.io.alus(i).bits.destinationTag.poke(v.get.destinationTag)
        this.io.alus(i).bits.value.poke(v.get.value)
      }
    }
  }


  def setDecoder(decoderValues: Seq[DecoderValue] = Seq.fill(params.numberOfDecoders)(new DecoderValue())): Unit = {
    for (i <- 0 until params.numberOfDecoders) {
      val decoder = this.io.decoders(i)
      val values = decoderValues(i)
      decoder.valid.poke(values.valid)
      decoder.source1.sourceRegister.poke(values.source1)
      decoder.source2.sourceRegister.poke(values.source2)
      decoder.destination.destinationRegister.poke(values.destination)
    }
  }


  def expectDecoder(values: Seq[Option[DecoderExpect]]): Unit = {

  }


  def expectRegisterFile(outputs: Seq[Option[RegisterFileValue]]): Unit = {
    for (i <- 0 until params.maxRegisterFileCommitCount) {
      this.io.registerFile(i).valid.expect(outputs(i).isDefined)
      if (outputs(i).isDefined) {
        this.io.registerFile(i).bits.destinationRegister.expect(outputs(i).get.destinationTag)
        this.io.registerFile(i).bits.value.expect(outputs(i).get.value)
      }
    }
  }
}

class ALUValue(val destinationTag: Int = 0, val value: Int = 0)

class DecoderValue(val valid: Boolean = false,
                   val source1: Int = 0,
                   val source2: Int = 0,
                   val destination: Int = 0)

class DecoderExpect(val destinationTag: Int,
                    val sourceTag1: Option[Int],
                    val sourceTag2: Option[Int],
                    val value1: Option[Int],
                    val value2: Option[Int])

class RegisterFileValue(val destinationTag: Int = 0,
                        val value: Int = 0)

class ReorderBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Reorder Buffer"
  implicit val defaultParams = Parameters(tagWidth = 5, numberOfDecoders = 1, numberOfALUs = 1, maxRegisterFileCommitCount = 1, debug = true)

  it should "output nothing to register file on first clock" in {
    test(new ReorderBufferWrapper) {
      c =>
        c.initialize()
        c.expectRegisterFile(Seq(None))
    }
  }

  it should "set ready to 0 when full" in {
    test(new ReorderBufferWrapper) { c =>
      c.initialize()
      var loop = 0
      c.io.decoders(0).ready.expect(true)
      while (loop < 40 && c.io.decoders(0).ready.peek().litToBoolean) {
        //        println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
        c.setDecoder(Seq(new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32))))
        c.clock.step()
        loop += 1
      }
      c.io.decoders(0).ready.expect(false)
    }
  }

  it should "set ready to 0 when full with 4 decoders (reduced tag width to 5 for speed)" in {
    test(new ReorderBufferWrapper()(defaultParams.copy(numberOfDecoders = 4))) { c =>
      c.initialize()
      var loop = 0
      c.io.decoders(0).ready.expect(true)
      while (loop < 40 && c.io.decoders(0).ready.peek().litToBoolean) {
        //        println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
        c.setDecoder(Seq(
          new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32)),
          new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32)),
          new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32)),
          new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32)),
        ))
        c.clock.step()
        loop += 1
      }
      //      println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
      c.io.decoders(0).ready.expect(false)
    }
  }

  it should "set ready to 0 when full with 4 decoders but 2 decoder inputs (reduced tag width to 5 for speed)" in {
    test(new ReorderBufferWrapper()(defaultParams.copy(numberOfDecoders = 4))) { c =>
      c.initialize()
      var loop = 0
      c.io.decoders(0).ready.expect(true)
      while (loop < 40 && c.io.decoders(0).ready.peek().litToBoolean) {
        //        println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
        c.setDecoder(Seq(
          new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32)),
          new DecoderValue(valid = true, source1 = Random.nextInt(32), source2 = Random.nextInt(32), destination = Random.nextInt(32)),
          new DecoderValue(valid = false, source1 = 0, source2 = 0, destination = 0),
          new DecoderValue(valid = false, source1 = 0, source2 = 0, destination = 0),
        ))
        c.clock.step()
        loop += 1
      }
      //      println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
      c.io.decoders(0).ready.expect(false)
    }
  }

  it should "make the tail follow head" in {
    test(new ReorderBufferWrapper) { c =>
      c.initialize()
      c.io.decoders(0).ready.expect(true)
      c.io.tail.get.expect(0)
      //      println(c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
      c.setDecoder(Seq(
        new DecoderValue(valid = true, destination = 1, source1 = 2, source2 = 3),
      ))
      c.setALU(Seq(Some(new ALUValue(destinationTag = 0, value = 3))))
      c.clock.step(2)

      //      println(c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
      c.io.tail.get.expect(1)
    }
  }
}
