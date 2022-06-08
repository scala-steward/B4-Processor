package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.utils.{DecodeEnqueue, LSQ2Memory, LSQfromALU}
import b4processor.utils.ExecutorValue
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LoadStoreQueueWrapper(implicit params: Parameters) extends LoadStoreQueue {

  def initialize(): Unit = {
    this.setDecoder()
    this.setExecutor()
    this.setReorderBuffer()
  }

  def setExecutor(values: Seq[Option[LSQfromALU]] = Seq.fill(params.runParallel)(None)): Unit = {
    for (i <- 0 until params.runParallel) {
      val executor = this.io.executors(i)
      val value = values(i)
      executor.valid.poke(value.exists(_.valid))
      executor.value.poke(value.map(_.value).getOrElse(0))
      executor.destinationTag.poke(value.map(_.destinationtag).getOrElse(0))
      executor.programCounter.poke(value.map(_.ProgramCounter).getOrElse(0))
    }
  }

  def setDecoder(values: Seq[Option[DecodeEnqueue]] = Seq.fill(params.runParallel)(None)): Unit = {
    for (i <- 0 until params.runParallel) {
      val decoder = this.io.decoders(i)
      val value = values(i)
      decoder.valid.poke(value.isDefined)
      decoder.bits.stag2.poke(value.map(_.stag2).getOrElse(0))
      decoder.bits.value.poke(value.map(_.value).getOrElse(0))
      decoder.bits.opcode.poke(value.map(_.opcode).getOrElse(0))
      decoder.bits.programCounter.poke(value.map(_.ProgramCounter).getOrElse(0))
      decoder.bits.function3.poke(value.map(_.function3).getOrElse(0))
    }
  }

  def setReorderBuffer(ProgramCounters: Seq[Int] = Seq.fill(params.runParallel)(0),
                       valids: Seq[Boolean] = Seq.fill(params.runParallel)(false)): Unit = {
    for (i <- 0 until params.runParallel) {
      val pc = ProgramCounters(i)
      val v = valids(i)
      io.reorderBuffer.programCounter(i).poke(pc)
      io.reorderBuffer.valid(i).poke(v)
    }
  }

  def expectMemory(values: Seq[Option[LSQ2Memory]]): Unit = {
    for (i <- 0 until params.maxRegisterFileCommitCount) {
      if (values(i).isDefined) {
        this.io.memory(i).valid.expect(values(i).isDefined)
        this.io.memory(i).bits.address.expect(values(i).get.address)
        this.io.memory(i).bits.tag.expect(values(i).get.tag)
        this.io.memory(i).bits.opcode.expect(values(i).get.opcode)
        this.io.memory(i).bits.function3.expect(values(i).get.function3)
        this.io.memory(i).bits.data.expect(values(i).get.data)
      }
    }
  }
}

class LoadStoreQueueTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Load Store Queue"
  implicit val defaultParams = Parameters(tagWidth = 4, runParallel = 1, maxRegisterFileCommitCount = 1, debug = true)

  it should "Both Of Instructions Enqueue LSQ" in {
    test(new LoadStoreQueueWrapper) { c =>
      c.initialize()
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
      c.io.decoders(0).ready.expect(true)
      // c.io.decoders(1).ready.expect(true)
      c.setDecoder(values =
        Seq(Some(DecodeEnqueue(stag2 = 10, value = 0, opcode = 3, ProgramCounter = 100, function3 = 0)),
          Some(DecodeEnqueue(stag2 = 11, value = 0, opcode = 3, ProgramCounter = 104, function3 = 0))))
      c.clock.step(1)
      c.io.head.get.expect(1)
      c.io.tail.get.expect(0)
    }
  }

  it should "invalid enqueue" in {
    test(new LoadStoreQueueWrapper) { c =>
      c.initialize()
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
      c.io.decoders(0).ready.expect(true)
      // c.io.decoders(1).ready.expect(true)
      c.setDecoder(values =
        Seq(Some(DecodeEnqueue(stag2 = 10, value = 0, opcode = 33, ProgramCounter = 100, function3 = 0)),
        ))
      c.clock.step(1)
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
    }
  }

  it should "load check" in {
    test(new LoadStoreQueueWrapper) { c =>
      c.initialize()
      // 初期化
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
      c.io.decoders(0).ready.expect(true)
      c.io.memory(0).ready.poke(true)
      c.expectMemory(Seq(None))

      // 値のセット
      c.setDecoder(values =
        Seq(Some(DecodeEnqueue(stag2 = 10, value = 0, opcode = 3, ProgramCounter = 100, function3 = 0)),
        ))
      c.clock.step(1)

      // 値の確認
      c.setExecutor(values = Seq(
        Some(LSQfromALU(valid = true, destinationtag = 10, value = 150, ProgramCounter = 100)
        )))
      c.io.head.get.expect(1)

      c.clock.step(1)

      c.expectMemory(values =
        Seq(Some(LSQ2Memory(address = 150, tag = 10, data = 0, opcode = 3, function3 = 0))))

      c.clock.step(3)

    }
  }

  it should "store check" in {
    test(new LoadStoreQueueWrapper) { c =>
      c.initialize()
      // 初期化
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
      c.io.decoders(0).ready.expect(true)
      c.io.memory(0).ready.poke(true)
      c.expectMemory(Seq(None))

      // 値のセット
      c.setDecoder(values =
        Seq(Some(DecodeEnqueue(stag2 = 10, value = 0, opcode = 35, ProgramCounter = 100, function3 = 0)),
        ))

      c.clock.step(1)

      // 値の確認
      c.setExecutor(values = Seq(
        Some(LSQfromALU(valid = true, destinationtag = 10, value = 123, ProgramCounter = 80)
        )))
      c.io.head.get.expect(1)

      c.clock.step(1)

      c.setExecutor(values = Seq(
        Some(LSQfromALU(valid = true, destinationtag = 10, value = 150, ProgramCounter = 100)
        )))
      c.setReorderBuffer(valids = Seq(true), ProgramCounters = Seq(100))
      c.io.head.get.expect(2)

      c.clock.step(1)

      c.expectMemory(values =
        Seq(Some(LSQ2Memory(address = 150, tag = 10, data = 123, opcode = 35, function3 = 0))))

      c.clock.step(2)
    }
  }

}
