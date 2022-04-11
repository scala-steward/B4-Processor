package b4processor.decoder

import b4processor.modules.decoder.Decoder
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ALUValue(val destinationTag: Int = 0, val value: Int = 0)

/**
 * デコーダをテストしやすくするためにラップしたもの
 *
 * @param instruction_offset 同時に扱う命令のうちいくつ目の命令を担当するか
 * @param number_of_alus     ALUの数
 */
class DecoderWrapper(instruction_offset: Int = 0, number_of_alus: Int = 0) extends Decoder(instruction_offset, number_of_alus) {
  def initialize(instruction: UInt): Unit = {
    this.setImem(instruction)
    this.setReorderBuffer()
    this.setRegisterFile()
    this.setALU()
  }

  def setImem(instruction: UInt): Unit = {
    this.io.imem.bits.program_counter.poke(0)
    this.io.imem.bits.instruction.poke(instruction)
    this.io.imem.valid.poke(true)
  }

  def setReorderBuffer(destinationTag: Int = 0,
                       sourceTag1: Option[Int] = None,
                       value1: Option[Int] = None,
                       sourceTag2: Option[Int] = None,
                       value2: Option[Int] = None): Unit = {
    this.io.reorderBuffer.destination.destinationTag.poke(destinationTag)
    this.io.reorderBuffer.source1.matchingTag.valid.poke(sourceTag1.isDefined)
    this.io.reorderBuffer.source1.matchingTag.bits.poke(sourceTag1.getOrElse(0))
    this.io.reorderBuffer.source1.value.valid.poke(value1.isDefined)
    this.io.reorderBuffer.source1.value.bits.poke(value1.getOrElse(0))
    this.io.reorderBuffer.source2.matchingTag.valid.poke(sourceTag2.isDefined)
    this.io.reorderBuffer.source2.matchingTag.bits.poke(sourceTag2.getOrElse(0))
    this.io.reorderBuffer.source2.value.valid.poke(value2.isDefined)
    this.io.reorderBuffer.source2.value.bits.poke(value2.getOrElse(0))
    this.io.reorderBuffer.ready.poke(true)
  }

  def setRegisterFile(value1: Int = 0, value2: Int = 0): Unit = {
    this.io.registerFile.value1.poke(value1)
    this.io.registerFile.value2.poke(value2)
  }

  def setALU(bypassedValues: Seq[Option[ALUValue]] = Seq.fill(number_of_alus)(None)) = {
    for (i <- bypassedValues.indices) {
      this.io.alu(i).valid.poke(bypassedValues(i).isDefined)
      this.io.alu(i).bits.destinationTag.poke(bypassedValues(i).getOrElse(new ALUValue).destinationTag)
      this.io.alu(i).bits.value.poke(bypassedValues(i).getOrElse(new ALUValue).value)
    }
  }


  def expectReorderBuffer(destinationRegister: Int = 0, sourceRegister1: Int = 0, sourceRegister2: Int = 0): Unit = {
    // check rd
    this.io.reorderBuffer.destination.destinationRegister.expect(destinationRegister, "rdの値が間違っています")
    // check rs1
    this.io.reorderBuffer.source1.sourceRegister.expect(sourceRegister1, "rs1 doesn't match")
    // check rs2
    this.io.reorderBuffer.source2.sourceRegister.expect(sourceRegister2, "rs2 doesn't match")
  }

  def expectReservationStation(destinationTag: Int = 0,
                               sourceTag1: Int = 0,
                               sourceTag2: Int = 0,
                               value1: Int = 0,
                               value2: Int = 0,
                               immediateOrFunction7: Int = 0): Unit = {
    this.io.reservationStation.bits.destinationTag.expect(destinationTag)
    this.io.reservationStation.bits.sourceTag1.expect(sourceTag1)
    this.io.reservationStation.bits.sourceTag2.expect(sourceTag2)
    this.io.reservationStation.bits.value1.expect(value1)
    this.io.reservationStation.bits.value2.expect(value2)
    this.io.reservationStation.bits.immediateOrFunction7.expect(immediateOrFunction7)
  }
}

/**
 * デコーダのテスト
 */
class DecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "decoder"

  it should "pass rs1 rs2 rd to reorder buffer" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.expectReorderBuffer(destinationRegister = 1, sourceRegister1 = 2, sourceRegister2 = 3)
    }
  }

  it should "get values from register file" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setRegisterFile(value1 = 10, value2 = 20)

      c.expectReorderBuffer(destinationRegister = 1, sourceRegister1 = 2, sourceRegister2 = 3)
      c.expectReservationStation(value1 = 10, value2 = 20)
    }
  }

  it should "get source tags from reorder buffer" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setReorderBuffer(destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7))

      c.expectReorderBuffer(destinationRegister = 1, sourceRegister1 = 2, sourceRegister2 = 3)
      c.expectReservationStation(destinationTag = 5, sourceTag1 = 6, sourceTag2 = 7)
    }
  }

  it should "get source tags and values from reorder buffer" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setReorderBuffer(
        destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7), value1 = Some(20), value2 = Some(21))

      c.expectReorderBuffer(destinationRegister = 1, sourceRegister1 = 2, sourceRegister2 = 3)
      c.expectReservationStation(
        destinationTag = 5, value1 = 20, value2 = 21)
    }
  }

  it should "understand sd" in {
    test(new DecoderWrapper(0)) { c =>
      // sd x1,10(x2)
      c.initialize(0x00113523.U)
      c.expectReorderBuffer(sourceRegister1 = 2, sourceRegister2 = 1)
      c.expectReservationStation(immediateOrFunction7 = 10)
    }
  }

  it should "understand immediate" in {
    test(new DecoderWrapper(0)) { c =>
      // addi x1,x2,20
      c.initialize(0x01410093.U)
      c.setReorderBuffer(destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7))

      c.expectReorderBuffer(1, sourceRegister1 = 2)
      c.expectReservationStation(destinationTag = 5, sourceTag1 = 6, value2 = 20)
    }
  }

  it should "do register bypass" in {
    test(new DecoderWrapper(0, 2)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setReorderBuffer(destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7))
      c.setALU(Seq(Some(new ALUValue(6, 20)), Some(new ALUValue(7, 21))))

      c.expectReorderBuffer(destinationRegister = 1, sourceRegister1 = 2, sourceRegister2 = 3)
      c.expectReservationStation(destinationTag = 5, value1 = 20, value2 = 21)
    }
  }

  it should "say the data is valid" in {
    test(new DecoderWrapper(0, 2)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)

      c.io.reorderBuffer.valid.expect(true.B)
      c.io.reservationStation.valid.expect(true.B)
    }
  }

  it should "say the data is invalid" in {
    test(new DecoderWrapper(0, 2)) { c =>
      c.initialize(0.U)
      c.io.imem.valid.poke(false.B)

      c.io.reorderBuffer.valid.expect(false.B)
      c.io.reservationStation.valid.expect(false.B)
    }
  }

  it should "understand U format" in {
    test(new DecoderWrapper(0, 0)) { c =>
      // lui x3, 123
      c.initialize("x0007b1b7".U)
      c.setReorderBuffer(destinationTag = 5)

      c.expectReorderBuffer(destinationRegister = 3)
      c.expectReservationStation(destinationTag = 5, value2 = 123 << 12)
    }
  }

  it should "understand J format" in {
    test(new DecoderWrapper()) { c =>
      // jal x10,LABEL
      // LABEL:
      c.initialize("x0040056f".U)
      c.setReorderBuffer(destinationTag = 5)

      c.expectReorderBuffer(destinationRegister = 10)
      c.expectReservationStation(destinationTag = 5, value2 = 4)
    }
  }
}
