package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.utils.ALUValue
import chiseltest._
import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec

class ReservationStationWrapper(implicit params: Parameters) extends ReservationStation {
  def initialize(): Unit = {
    setExecutorReady(true)
    setDecoderInput(None)
    setALUs(Seq.fill(params.numberOfALUs)(None))
  }

  def setExecutorReady(value: Boolean): Unit = {
    this.io.executor.ready.poke(value)
  }

  def setDecoderInput(programCounter: Option[Int], value1: Option[Int] = None, value2: Option[Int] = None): Unit = {
    this.io.decoder.valid.poke(programCounter.isDefined)
    this.io.decoder.bits.valid.poke(programCounter.isDefined)
    this.io.decoder.bits.elements.foreach { case (name, value) => value.poke(0.U) }
    if (programCounter.isDefined)
      this.io.decoder.bits.programCounter.poke(programCounter.get)
    if (value1.isDefined) {
      this.io.decoder.bits.value1.poke(value1.get)
      this.io.decoder.bits.ready1.poke(true)
    }
    if (value2.isDefined) {
      this.io.decoder.bits.value2.poke(value2.get)
      this.io.decoder.bits.ready2.poke(true)
    }

  }

  def setALUs(values: Seq[Option[ALUValue]]): Unit = {
    for ((alu, v) <- io.alus.zip(values)) {
      alu.valid.poke(v.isDefined)
      alu.value.poke(v.getOrElse(ALUValue(destinationTag = 0, value = 0)).value)
      alu.destinationTag.poke(v.getOrElse(ALUValue(destinationTag = 0, value = 0)).destinationTag)
    }
  }

  def expectExecutor(programCounter: Option[Int]): Unit = {
    this.io.executor.valid.expect(programCounter.isDefined)
    if (programCounter.isDefined)
      this.io.executor.bits.programCounter.expect(programCounter.get)
  }
}

class ReservationStationTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "reservation station"
  implicit val params = Parameters(numberOfALUs = 1, numberOfReservationStationEntries = 5)

  // エントリを追加してALUから値をうけとり、実行ユニットに回す
  it should "store a entry and release it" in {
    test(new ReservationStationWrapper()) { c =>
      c.initialize()
      c.setDecoderInput(programCounter = Some(1))
      c.clock.step()
      c.setDecoderInput(None)
      c.setALUs(Seq(Some(ALUValue(destinationTag = 0, value = 0))))
      c.expectExecutor(None)
      c.clock.step()
      c.expectExecutor(Some(1))
      c.clock.step(5)
      c.expectExecutor(None)
    }
  }

  // RSをバイパスしてデコーダから直接実行ユニットに行く
  it should "bypass the RS" in {
    test(new ReservationStationWrapper()) { c =>
      c.initialize()
      c.setDecoderInput(programCounter = Some(1), value1 = Some(2), value2 = Some(3))
      c.expectExecutor(Some(1))

      c.clock.step()
      c.setDecoderInput(programCounter = None)
      c.expectExecutor(None)

      c.clock.step()
      c.expectExecutor(None)
      c.setALUs(Seq(Some(ALUValue(destinationTag = 0, value = 0))))

      c.clock.step()
      c.expectExecutor(None)
    }
  }

  // 空きがないときready=0になる
  it should "make decoder become not ready" in {
    test(new ReservationStationWrapper()) { c =>
      c.initialize()
      c.setExecutorReady(false)
      var loop = 0
      while (loop < 100 && c.io.decoder.ready.peek().litToBoolean) {
        loop += 1
        c.setDecoderInput(programCounter = Some(1), value1 = Some(2), value2 = Some(3))
        c.clock.step()
      }
      c.io.decoder.ready.expect(false)
    }
  }
}
