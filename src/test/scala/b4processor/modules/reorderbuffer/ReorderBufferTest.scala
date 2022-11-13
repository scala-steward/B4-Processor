package b4processor.modules.reorderbuffer

import b4processor.Parameters
import b4processor.utils.{DecoderValue, ExecutorValue, RegisterFileValue, Tag}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

class ReorderBufferWrapper(implicit params: Parameters) extends ReorderBuffer {
  def initialize(): Unit = {
    setOutputs()
    setDecoder()
  }

  def setOutputs(
    values: Seq[Option[ExecutorValue]] = Seq.fill(params.runParallel + 1)(None)
  ): Unit = {
    for (i <- 0 until params.runParallel + 1) {
      val output = this.io.collectedOutputs.outputs(i)
      val v = values(i)
      output.validAsResult.poke(v.isDefined)
      if (v.isDefined) {
        output.tag.poke(Tag(v.get.destinationTag))
        output.value.poke(v.get.value)
      }
    }
  }

  def setDecoder(
    decoderValues: Seq[DecoderValue] =
      Seq.fill(params.runParallel)(DecoderValue())
  ): Unit = {
    for (i <- 0 until params.runParallel) {
      val decoder = this.io.decoders(i)
      val values = decoderValues(i)
      decoder.valid.poke(values.valid)
      decoder.source1.sourceRegister.poke(values.source1)
      decoder.source2.sourceRegister.poke(values.source2)
      decoder.destination.destinationRegister.poke(values.destination)
      decoder.programCounter.poke(values.programCounter)
    }
  }

  def expectRegisterFile(outputs: Seq[Option[RegisterFileValue]]): Unit = {
    for (i <- 0 until params.maxRegisterFileCommitCount) {
      this.io.registerFile(i).valid.expect(outputs(i).isDefined)
      if (outputs(i).isDefined) {
        this.io
          .registerFile(i)
          .bits
          .destinationRegister
          .expect(outputs(i).get.destinationRegister)
        this.io.registerFile(i).bits.value.expect(outputs(i).get.value)
      }
    }
  }

  def printRF(): Unit = {
    for (i <- 0 until params.maxRegisterFileCommitCount)
      println(s"rf valid=${this.io.registerFile(i).valid.peek()} rd=${this.io
          .registerFile(i)
          .bits
          .destinationRegister
          .peek()}, value=${this.io.registerFile(i).bits.value.peek()}")
  }
}

class ReorderBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Reorder Buffer"
  implicit val defaultParams = Parameters(
    tagWidth = 4,
    runParallel = 1,
    maxRegisterFileCommitCount = 1,
    debug = true
  )

  /** リオーダバッファに値が出力されない */
  it should "output nothing to register file on first clock" in {
    test(new ReorderBufferWrapper) { c =>
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
        c.setDecoder(
          Seq(
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            )
          )
        )
        c.clock.step()
        loop += 1
      }
      c.io.decoders(0).ready.expect(false)
    }
  }

  it should "set ready to 0 when full with 4 decoders (reduced tag width to 5 for speed)" in {
    test(new ReorderBufferWrapper()(defaultParams.copy(runParallel = 4))) { c =>
      c.initialize()
      var loop = 0
      c.io.decoders(0).ready.expect(true)
      while (loop < 40 && c.io.decoders(0).ready.peek().litToBoolean) {
        //        println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
        c.setDecoder(
          Seq(
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            ),
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            ),
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            ),
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            )
          )
        )
        c.clock.step()
        loop += 1
      }
      //      println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
      c.io.decoders(0).ready.expect(false)
    }
  }

  it should "set ready to 0 when full with 4 decoders but 2 decoder inputs (reduced tag width to 5 for speed)" in {
    test(new ReorderBufferWrapper()(defaultParams.copy(runParallel = 4))) { c =>
      c.initialize()
      var loop = 0
      c.io.decoders(0).ready.expect(true)
      while (loop < 40 && c.io.decoders(0).ready.peek().litToBoolean) {
        //        println(loop, c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
        c.setDecoder(
          Seq(
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            ),
            DecoderValue(
              valid = true,
              source1 = Random.nextInt(32),
              source2 = Random.nextInt(32),
              destination = Random.nextInt(32)
            ),
            DecoderValue(),
            DecoderValue()
          )
        )
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
      c.setDecoder(
        Seq(
          DecoderValue(valid = true, destination = 1, source1 = 2, source2 = 3)
        )
      )
      c.setOutputs(
        Seq(Some(ExecutorValue(destinationTag = 0, value = 3)), None)
      )
      c.clock.step(2)

      //      println(c.io.head.get.peek().litValue, c.io.tail.get.peek().litValue)
      c.io.tail.get.expect(1)
    }
  }

  it should "have an output in register file" in {
    test(new ReorderBufferWrapper).withAnnotations(Seq(WriteFstAnnotation)) {
      c =>
        c.initialize()
        c.clock.setTimeout(10)

        // 値の確認
        c.io.head.get.expect(0)
        c.io.tail.get.expect(0)
        c.expectRegisterFile(Seq(None))

        // 値のセット
        c.setDecoder(
          Seq(
            DecoderValue(
              valid = true,
              destination = 1,
              source1 = 2,
              source2 = 3,
              programCounter = 500
            )
          )
        )

        c.clock.step()
        // 値の確認
        c.expectRegisterFile(Seq(None))
        c.io.head.get.expect(1)

        // 値のセット
        c.setDecoder(Seq(DecoderValue()))
        c.setOutputs(
          Seq(Some(ExecutorValue(destinationTag = 0, value = 10)), None)
        )

        c.clock.step()
        c.setOutputs(Seq(None, None))
        // 値の確認
        c.expectRegisterFile(
          Seq(Some(RegisterFileValue(destinationRegister = 1, value = 10)))
        )

        c.clock.step(5)
    }
  }

  //  it should "have an output in register file with prediction" in {
  //    test(new ReorderBufferWrapper).withAnnotations(Seq(WriteFstAnnotation)) { c =>
  //      c.initialize()
  //      c.clock.setTimeout(10)
  //
  //      // 値の確認
  //      c.io.head.get.expect(0)
  //      c.io.tail.get.expect(0)
  //      c.expectRegisterFile(Seq(None))
  //
  //      // 値のセット
  //      c.setDecoder(Seq(DecoderValue(valid = true, destination = 1, source1 = 2, source2 = 3, programCounter = 500, isPrediction = true)))
  //
  //      c.clock.step()
  //      // 値の確認
  //      c.expectRegisterFile(Seq(None))
  //      c.io.head.get.expect(1)
  //
  //      // 値のセット
  //      c.setDecoder(Seq(DecoderValue()))
  //      c.setALU(Seq(Some(ALUValue(destinationTag = 0, value = 10))))
  //
  //      c.clock.step()
  //      c.setALU(Seq(None))
  //      // 値の確認
  //      c.expectRegisterFile(Seq(None))
  //
  //      c.clock.step()
  //
  //      c.expectRegisterFile(Seq(Some(RegisterFileValue(destinationRegister = 1, value = 10))))
  //
  //      c.clock.step(5)
  //    }
  //  }

  it should "have an output in register file with 4 of each component" in {
    test(
      new ReorderBufferWrapper()(
        defaultParams.copy(runParallel = 4, maxRegisterFileCommitCount = 4)
      )
    ).withAnnotations(Seq(WriteFstAnnotation)) { c =>
      c.initialize()
      c.clock.setTimeout(10)

      // 値の確認
      c.expectRegisterFile(Seq(None, None, None, None))

      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 1,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          ),
          DecoderValue(
            valid = true,
            destination = 2,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          ),
          DecoderValue(
            valid = true,
            destination = 3,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          ),
          DecoderValue(
            valid = true,
            destination = 4,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          )
        )
      )

      c.clock.step()
      // 値の確認
      c.expectRegisterFile(Seq(None, None, None, None))

      // 値のセット
      c.setDecoder(Seq.fill(4)(DecoderValue()))
      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 0, value = 10)),
          Some(ExecutorValue(destinationTag = 1, value = 20)),
          Some(ExecutorValue(destinationTag = 2, value = 30)),
          Some(ExecutorValue(destinationTag = 3, value = 40)),
          None
        )
      )

      c.clock.step()
      c.setOutputs(Seq(None, None, None, None, None))
      c.io.registerFile(0).valid.expect(true)
      // 値の確認
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 1, value = 10)),
          Some(RegisterFileValue(destinationRegister = 2, value = 20)),
          Some(RegisterFileValue(destinationRegister = 3, value = 30)),
          Some(RegisterFileValue(destinationRegister = 4, value = 40))
        )
      )

      c.clock.step(5)
    }
  }

  it should "have an output in register file with 4 of each component out of order simple" in {
    test(
      new ReorderBufferWrapper()(
        defaultParams.copy(runParallel = 4, maxRegisterFileCommitCount = 4)
      )
    ).withAnnotations(Seq(WriteFstAnnotation)) { c =>
      c.initialize()
      c.clock.setTimeout(10)

      // 値の確認
      c.expectRegisterFile(Seq(None, None, None, None))

      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 1,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          ),
          DecoderValue(
            valid = true,
            destination = 2,
            source1 = 2,
            source2 = 3,
            programCounter = 504
          ),
          DecoderValue(
            valid = true,
            destination = 3,
            source1 = 2,
            source2 = 3,
            programCounter = 508
          ),
          DecoderValue(
            valid = true,
            destination = 4,
            source1 = 2,
            source2 = 3,
            programCounter = 512
          )
        )
      )

      c.clock.step()

      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 5,
            source1 = 2,
            source2 = 3,
            programCounter = 516
          ),
          DecoderValue(
            valid = true,
            destination = 6,
            source1 = 2,
            source2 = 3,
            programCounter = 520
          ),
          DecoderValue(
            valid = true,
            destination = 7,
            source1 = 2,
            source2 = 3,
            programCounter = 524
          ),
          DecoderValue(
            valid = true,
            destination = 8,
            source1 = 2,
            source2 = 3,
            programCounter = 528
          )
        )
      )
      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 4, value = 50)),
          Some(ExecutorValue(destinationTag = 5, value = 60)),
          Some(ExecutorValue(destinationTag = 6, value = 70)),
          Some(ExecutorValue(destinationTag = 7, value = 80)),
          None
        )
      )

      c.clock.step()
      c.setDecoder()
      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 0, value = 10)),
          Some(ExecutorValue(destinationTag = 1, value = 20)),
          Some(ExecutorValue(destinationTag = 2, value = 30)),
          Some(ExecutorValue(destinationTag = 3, value = 40)),
          None
        )
      )

      c.clock.step()
      c.setOutputs(Seq(None, None, None, None, None))
      // 値の確認
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 1, value = 10)),
          Some(RegisterFileValue(destinationRegister = 2, value = 20)),
          Some(RegisterFileValue(destinationRegister = 3, value = 30)),
          Some(RegisterFileValue(destinationRegister = 4, value = 40))
        )
      )

      c.clock.step()
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 5, value = 50)),
          Some(RegisterFileValue(destinationRegister = 6, value = 60)),
          Some(RegisterFileValue(destinationRegister = 7, value = 70)),
          Some(RegisterFileValue(destinationRegister = 8, value = 80))
        )
      )

      c.clock.step(5)
    }
  }

  it should "have an output in register file with 4 of each component out of order complex" in {
    test(
      new ReorderBufferWrapper()(
        defaultParams.copy(runParallel = 4, maxRegisterFileCommitCount = 4)
      )
    ).withAnnotations(Seq(WriteFstAnnotation)) { c =>
      c.initialize()
      c.clock.setTimeout(10)

      // 値の確認
      c.expectRegisterFile(Seq(None, None, None, None))

      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 1,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          ),
          DecoderValue(
            valid = true,
            destination = 2,
            source1 = 2,
            source2 = 3,
            programCounter = 504
          ),
          DecoderValue(
            valid = true,
            destination = 3,
            source1 = 2,
            source2 = 3,
            programCounter = 508
          ),
          DecoderValue(
            valid = true,
            destination = 4,
            source1 = 2,
            source2 = 3,
            programCounter = 512
          )
        )
      )

      c.clock.step()

      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 5,
            source1 = 2,
            source2 = 3,
            programCounter = 516
          ),
          DecoderValue(
            valid = true,
            destination = 6,
            source1 = 2,
            source2 = 3,
            programCounter = 520
          ),
          DecoderValue(
            valid = true,
            destination = 7,
            source1 = 2,
            source2 = 3,
            programCounter = 524
          ),
          DecoderValue(
            valid = true,
            destination = 8,
            source1 = 2,
            source2 = 3,
            programCounter = 528
          )
        )
      )
      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 1, value = 20)),
          Some(ExecutorValue(destinationTag = 5, value = 60)),
          Some(ExecutorValue(destinationTag = 7, value = 80)),
          Some(ExecutorValue(destinationTag = 2, value = 30)),
          None
        )
      )

      c.clock.step()
      c.setDecoder()
      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 0, value = 10)),
          Some(ExecutorValue(destinationTag = 6, value = 70)),
          Some(ExecutorValue(destinationTag = 4, value = 50)),
          Some(ExecutorValue(destinationTag = 3, value = 40)),
          None
        )
      )

      c.clock.step()
      c.setOutputs(Seq(None, None, None, None, None))
      // 値の確認
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 1, value = 10)),
          Some(RegisterFileValue(destinationRegister = 2, value = 20)),
          Some(RegisterFileValue(destinationRegister = 3, value = 30)),
          Some(RegisterFileValue(destinationRegister = 4, value = 40))
        )
      )

      c.clock.step()
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 5, value = 50)),
          Some(RegisterFileValue(destinationRegister = 6, value = 60)),
          Some(RegisterFileValue(destinationRegister = 7, value = 70)),
          Some(RegisterFileValue(destinationRegister = 8, value = 80))
        )
      )

      c.clock.step(5)
    }
  }

  it should "have an output in register file with 4 of each component out of order complex not aligned" in {
    test(
      new ReorderBufferWrapper()(
        defaultParams.copy(runParallel = 4, maxRegisterFileCommitCount = 4)
      )
    ).withAnnotations(Seq(WriteFstAnnotation)) { c =>
      c.initialize()
      c.clock.setTimeout(10)

      // 値の確認
      c.expectRegisterFile(Seq(None, None, None, None))
      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 11,
            source1 = 2,
            source2 = 3,
            programCounter = 500
          ),
          DecoderValue(
            valid = true,
            destination = 12,
            source1 = 2,
            source2 = 3,
            programCounter = 504
          ),
          DecoderValue(
            valid = true,
            destination = 13,
            source1 = 2,
            source2 = 3,
            programCounter = 508
          ),
          DecoderValue(
            valid = true,
            destination = 14,
            source1 = 2,
            source2 = 3,
            programCounter = 512
          )
        )
      )

      c.clock.step()

      // 値のセット
      c.setDecoder(
        Seq(
          DecoderValue(
            valid = true,
            destination = 15,
            source1 = 2,
            source2 = 3,
            programCounter = 516
          ),
          DecoderValue(
            valid = true,
            destination = 16,
            source1 = 2,
            source2 = 3,
            programCounter = 520
          ),
          DecoderValue(
            valid = true,
            destination = 17,
            source1 = 2,
            source2 = 3,
            programCounter = 524
          ),
          DecoderValue(
            valid = true,
            destination = 18,
            source1 = 2,
            source2 = 3,
            programCounter = 528
          )
        )
      )
      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 0, value = 10)),
          Some(ExecutorValue(destinationTag = 1, value = 20)),
          Some(ExecutorValue(destinationTag = 5, value = 60)),
          Some(ExecutorValue(destinationTag = 4, value = 50)),
          None
        )
      )

      c.clock.step()

      c.setDecoder()
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 11, value = 10)),
          Some(RegisterFileValue(destinationRegister = 12, value = 20)),
          None,
          None
        )
      )

      c.setOutputs(
        Seq(
          Some(ExecutorValue(destinationTag = 6, value = 70)),
          Some(ExecutorValue(destinationTag = 3, value = 40)),
          Some(ExecutorValue(destinationTag = 7, value = 80)),
          Some(ExecutorValue(destinationTag = 2, value = 30)),
          None
        )
      )

      c.clock.step()
      c.setOutputs(Seq(None, None, None, None, None))
      // 値の確認
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 13, value = 30)),
          Some(RegisterFileValue(destinationRegister = 14, value = 40)),
          Some(RegisterFileValue(destinationRegister = 15, value = 50)),
          Some(RegisterFileValue(destinationRegister = 16, value = 60))
        )
      )

      c.clock.step()
      c.expectRegisterFile(
        Seq(
          Some(RegisterFileValue(destinationRegister = 17, value = 70)),
          Some(RegisterFileValue(destinationRegister = 18, value = 80)),
          None,
          None
        )
      )

      c.clock.step(5)
    }
  }

  //  it should "have an output in register file with 4 with correct predictions" in {
  //    test(new ReorderBufferWrapper()(defaultParams.copy(numberOfALUs = 4, numberOfDecoders = 4, maxRegisterFileCommitCount = 4))).withAnnotations(Seq(WriteFstAnnotation)) { c =>
  //      c.initialize()
  //      c.clock.setTimeout(10)
  //
  //      // 値の確認
  //      c.expectRegisterFile(Seq(None, None, None, None))
  //
  //      // 値のセット
  //      c.setDecoder(Seq(
  //        DecoderValue(valid = true, destination = 1, source1 = 2, source2 = 3, programCounter = 500),
  //        DecoderValue(valid = true, destination = 2, source1 = 2, source2 = 3, programCounter = 504),
  //        DecoderValue(valid = true, destination = 3, source1 = 2, source2 = 3, programCounter = 508, isPrediction = true),
  //        DecoderValue(valid = true, destination = 4, source1 = 2, source2 = 3, programCounter = 512, isPrediction = true),
  //      ))
  //
  //      c.clock.step()
  //
  //
  //      // 値のセット
  //      c.setDecoder(Seq(
  //        DecoderValue(valid = true, destination = 5, source1 = 2, source2 = 3, programCounter = 516, isPrediction = true),
  //        DecoderValue(valid = true, destination = 6, source1 = 2, source2 = 3, programCounter = 520, isPrediction = true),
  //        DecoderValue(valid = true, destination = 7, source1 = 2, source2 = 3, programCounter = 524, isPrediction = true),
  //        DecoderValue(valid = true, destination = 8, source1 = 2, source2 = 3, programCounter = 528, isPrediction = true),
  //      ))
  //      c.setALU(Seq(
  //        Some(ALUValue(destinationTag = 4, value = 50)),
  //        Some(ALUValue(destinationTag = 5, value = 60)),
  //        Some(ALUValue(destinationTag = 6, value = 70)),
  //        Some(ALUValue(destinationTag = 7, value = 80))
  //      ))
  //
  //      c.clock.step()
  //      c.setDecoder()
  //      c.setALU(Seq(
  //        Some(ALUValue(destinationTag = 0, value = 10)),
  //        Some(ALUValue(destinationTag = 1, value = 20)),
  //        Some(ALUValue(destinationTag = 2, value = 30)),
  //        Some(ALUValue(destinationTag = 3, value = 40))
  //      ))
  //
  //      c.clock.step()
  //      c.setALU(Seq(None, None, None, None))
  //      // 値の確認
  //      c.expectRegisterFile(Seq(
  //        Some(RegisterFileValue(destinationRegister = 1, value = 10)),
  //        Some(RegisterFileValue(destinationRegister = 2, value = 20)),
  //        None,
  //        None,
  //      ))
  //
  //      c.clock.step()
  //      c.expectRegisterFile(Seq(
  //        None,
  //        None,
  //        None,
  //        None,
  //      ))
  //
  //      c.clock.step()
  //
  //      c.clock.step()
  //      c.expectRegisterFile(Seq(
  //        Some(RegisterFileValue(destinationRegister = 3, value = 30)),
  //        Some(RegisterFileValue(destinationRegister = 4, value = 40)),
  //        Some(RegisterFileValue(destinationRegister = 5, value = 50)),
  //        Some(RegisterFileValue(destinationRegister = 6, value = 60)),
  //      ))
  //
  //      c.clock.step()
  //      c.expectRegisterFile(Seq(
  //        Some(RegisterFileValue(destinationRegister = 7, value = 70)),
  //        Some(RegisterFileValue(destinationRegister = 8, value = 80)),
  //        None,
  //        None,
  //      ))
  //
  //      c.clock.step(5)
  //    }
  //  }

  //  it should "have an output in register file with 4 with miss prediction" in {
  //    test(new ReorderBufferWrapper()(defaultParams.copy(numberOfALUs = 4, numberOfDecoders = 4, maxRegisterFileCommitCount = 4))).withAnnotations(Seq(WriteFstAnnotation)) { c =>
  //      c.initialize()
  //      c.clock.setTimeout(10)
  //
  //      // 値の確認
  //      c.expectRegisterFile(Seq(None, None, None, None))
  //
  //      // 値のセット
  //      c.setDecoder(Seq(
  //        DecoderValue(valid = true, destination = 1, source1 = 2, source2 = 3, programCounter = 500),
  //        DecoderValue(valid = true, destination = 2, source1 = 2, source2 = 3, programCounter = 504),
  //        DecoderValue(valid = true, destination = 3, source1 = 2, source2 = 3, programCounter = 508, isPrediction = true),
  //        DecoderValue(valid = true, destination = 4, source1 = 2, source2 = 3, programCounter = 512, isPrediction = true),
  //      ))
  //
  //      c.clock.step()
  //
  //
  //      // 値のセット
  //      c.setDecoder(Seq(
  //        DecoderValue(valid = true, destination = 5, source1 = 2, source2 = 3, programCounter = 516, isPrediction = true),
  //        DecoderValue(valid = true, destination = 6, source1 = 2, source2 = 3, programCounter = 520, isPrediction = true),
  //        DecoderValue(valid = true, destination = 7, source1 = 2, source2 = 3, programCounter = 524, isPrediction = true),
  //        DecoderValue(valid = true, destination = 8, source1 = 2, source2 = 3, programCounter = 528, isPrediction = true),
  //      ))
  //      c.setALU(Seq(
  //        Some(ALUValue(destinationTag = 4, value = 50)),
  //        Some(ALUValue(destinationTag = 5, value = 60)),
  //        Some(ALUValue(destinationTag = 6, value = 70)),
  //        Some(ALUValue(destinationTag = 7, value = 80))
  //      ))
  //
  //      c.clock.step()
  //      c.setDecoder()
  //      c.setALU(Seq(
  //        Some(ALUValue(destinationTag = 0, value = 10)),
  //        Some(ALUValue(destinationTag = 1, value = 20)),
  //        Some(ALUValue(destinationTag = 2, value = 30)),
  //        Some(ALUValue(destinationTag = 3, value = 40))
  //      ))
  //
  //      c.clock.step()
  //      c.setALU(Seq(None, None, None, None))
  //      // 値の確認
  //      c.expectRegisterFile(Seq(
  //        Some(RegisterFileValue(destinationRegister = 1, value = 10)),
  //        Some(RegisterFileValue(destinationRegister = 2, value = 20)),
  //        None,
  //        None,
  //      ))
  //
  //      c.clock.step()
  //      c.expectRegisterFile(Seq(
  //        None,
  //        None,
  //        None,
  //        None,
  //      ))
  //
  //      c.clock.step()
  //      c.expectRegisterFile(Seq(
  //        None, None, None, None
  //      ))
  //
  //      c.clock.step(5)
  //    }
  //  }
}
