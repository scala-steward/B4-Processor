package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.utils.ExecutorValue
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ReservationStationWrapper(implicit params: Parameters)
    extends ReservationStation {
  def initialize(): Unit = {
    setExecutorReady(true)
    setDecoderInput(None)
    setExecutors(Seq.fill(params.runParallel)(None))
  }

  def setExecutorReady(value: Boolean): Unit = {
    this.io.executor.ready.poke(value)
  }

  def setDecoderInput(
    programCounter: Option[Int],
    value1: Option[Int] = None,
    value2: Option[Int] = None
  ): Unit = {
    this.io.decoder.entry.poke(ReservationStationEntry.default)
    this.io.decoder.entry.valid.poke(programCounter.isDefined)
    if (programCounter.isDefined)
      this.io.decoder.entry.programCounter.poke(programCounter.get)
    if (value1.isDefined) {
      this.io.decoder.entry.value1.poke(value1.get)
      this.io.decoder.entry.ready1.poke(true)
    }
    if (value2.isDefined) {
      this.io.decoder.entry.value2.poke(value2.get)
      this.io.decoder.entry.ready2.poke(true)
    }
  }

  def setExecutors(values: Seq[Option[ExecutorValue]]): Unit = {
    for ((bypassValue, v) <- io.collectedOutput.outputs.zip(values)) {
      bypassValue.validAsResult.poke(v.isDefined)
      bypassValue.value.poke(
        v.getOrElse(ExecutorValue(destinationTag = 0, value = 0)).value
      )
      bypassValue.tag.poke(
        v.getOrElse(ExecutorValue(destinationTag = 0, value = 0)).destinationTag
      )
    }
  }

  def expectExecutor(programCounter: Option[Int]): Unit = {
    this.io.executor.valid.expect(programCounter.isDefined)
    if (programCounter.isDefined)
      this.io.executor.bits.programCounter.expect(programCounter.get)
  }
}

class ReservationStationTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Reservation Station"
  implicit val params = Parameters(runParallel = 1, tagWidth = 4)

  // エントリを追加してALUから値をうけとり、実行ユニットに回す
  it should "store a entry and release it" in {
    test(new ReservationStationWrapper())
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.initialize()
        c.setDecoderInput(programCounter = Some(1))
        c.clock.step()
        c.setDecoderInput(None)
        c.setExecutors(Seq(Some(ExecutorValue(destinationTag = 0, value = 0))))
        c.expectExecutor(None)
        c.clock.step()
        c.expectExecutor(Some(1))
        c.clock.step()
        c.expectExecutor(None)
        c.clock.step()
      }
  }

  // 空きがないときready=0になる
  it should "make decoder become not ready" in {
    test(new ReservationStationWrapper())
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.initialize()
        c.setExecutorReady(false)
        var loop = 0
        while (loop < 100 && c.io.decoder.ready.peek().litToBoolean) {
          loop += 1
          c.setDecoderInput(
            programCounter = Some(1),
            value1 = Some(2),
            value2 = Some(3)
          )
          c.clock.step()
        }
        c.io.decoder.ready.expect(false)

        c.clock.step()
      }
  }

  // 空きがなくなるまで命令を入れて
  it should "fill and flush instructions" in {
    test(new ReservationStationWrapper())
      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
        c.initialize()
        c.setExecutorReady(false)
        var loop = 0
        while (loop < 100 && c.io.decoder.ready.peekBoolean()) {
          loop += 1
          c.setDecoderInput(
            programCounter = Some(1),
            value1 = Some(2),
            value2 = Some(3)
          )
          c.clock.step()
        }
        c.io.decoder.ready.expect(false)

        c.clock.step()

        c.setExecutorReady(true)
        c.setDecoderInput(None)
        loop = 0
        while (loop < 100 && c.io.executor.valid.peekBoolean()) {
          loop += 1
          c.clock.step()
        }
        c.io.executor.valid.expect(false)
      }
  }
}
