package b4processor.decoder

import b4processor.modules.decoder.Decoder
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec


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
  }

  def setImem(instruction: UInt): Unit = {
    this.io.imem.bits.program_counter.poke(0.U)
    this.io.imem.bits.instruction.poke(instruction)
    this.io.imem.valid.poke(true.B)
  }

  def setReorderBuffer(destinationTag: Int = 0,
                       sourceTag1: Option[Int] = None,
                       value1: Option[Int] = None,
                       sourceTag2: Option[Int] = None,
                       value2: Option[Int] = None): Unit = {
    this.io.reorderBuffer.bits.destination.destinationTag.poke(destinationTag.U)
    this.io.reorderBuffer.bits.source1.matchingTag.valid.poke(sourceTag1.isDefined.B)
    this.io.reorderBuffer.bits.source1.matchingTag.bits.poke(sourceTag1.getOrElse(0).U)
    this.io.reorderBuffer.bits.source1.value.valid.poke(value1.isDefined.B)
    this.io.reorderBuffer.bits.source1.value.bits.poke(value1.getOrElse(0).U)
    this.io.reorderBuffer.bits.source2.matchingTag.valid.poke(sourceTag2.isDefined.B)
    this.io.reorderBuffer.bits.source2.matchingTag.bits.poke(sourceTag2.getOrElse(0).U)
    this.io.reorderBuffer.bits.source2.value.valid.poke(value2.isDefined.B)
    this.io.reorderBuffer.bits.source2.value.bits.poke(value2.getOrElse(0).U)
    this.io.reorderBuffer.ready.poke(true.B)
  }

  def setRegisterFile(value1: Int = 0, value2: Int = 0): Unit = {
    this.io.registerFile.value1.poke(value1.U)
    this.io.registerFile.value2.poke(value2.U)
  }

  def setALU(bypassedValues: Seq[Option[(Int, Int)]]) = {
    for (i <- bypassedValues.indices) {
      this.io.alu(i).valid.poke(bypassedValues(i).isDefined.B)
      this.io.alu(i).bits.destinationTag.poke(bypassedValues(i).getOrElse((0, 0))._1.U)
      this.io.alu(i).bits.value.poke(bypassedValues(i).getOrElse((0, 0))._2.U)
    }
  }


  def expectReorderBuffer(rd: Option[Int], rs1: Option[Int] = None, rs2: Option[Int] = None): Unit = {
    // check rd
    this.io.reorderBuffer.bits.destination.destinationRegister.valid.expect(rd.isDefined.B, "rdが間違っています")
    if (rd.isDefined)
      this.io.reorderBuffer.bits.destination.destinationRegister.bits.expect(rd.get.U, "rdの値が間違っています")

    // check rs1
    if (rs1.isDefined)
      this.io.reorderBuffer.bits.source1.sourceRegister.expect(rs1.get.U, "rs1 doesn't match")
    // check rs2
    if (rs2.isDefined)
      this.io.reorderBuffer.bits.source2.sourceRegister.expect(rs2.get.U, "rs2 doesn't match")
  }

  def expectReservationStation(destinationTag: Option[Int] = None,
                               sourceTag1: Option[Int] = None,
                               sourceTag2: Option[Int] = None,
                               value1: Option[Int] = Some(0),
                               value2: Option[Int] = Some(0),
                               immediateOrFunction7: Option[Int] = None): Unit = {
    if (destinationTag.isDefined)
      this.io.reservationStation.bits.destinationTag.expect(destinationTag.get.U)
    if (sourceTag1.isDefined)
      this.io.reservationStation.bits.sourceTag1.expect(sourceTag1.get.U)
    if (sourceTag2.isDefined)
      this.io.reservationStation.bits.sourceTag2.expect(sourceTag2.get.U)
    if (value1.isDefined)
      this.io.reservationStation.bits.value1.expect(value1.get.U)
    if (value2.isDefined)
      this.io.reservationStation.bits.value2.expect(value2.get.U)
    if (immediateOrFunction7.isDefined)
      this.io.reservationStation.bits.immediateOrFunction7.expect(immediateOrFunction7.get.U)
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
      c.expectReorderBuffer(rd = Some(1), rs1 = Some(2), rs2 = Some(3))
    }
  }

  it should "get values from register file" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setRegisterFile(value1 = 10, value2 = 20)

      c.expectReorderBuffer(rd = Some(1), rs1 = Some(2), rs2 = Some(3))
      c.expectReservationStation(value1 = Some(10), value2 = Some(20))
    }
  }

  it should "get source tags from reorder buffer" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setReorderBuffer(destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7))

      c.expectReorderBuffer(rd = Some(1), rs1 = Some(2), rs2 = Some(3))
      c.expectReservationStation(destinationTag = Some(5), sourceTag1 = Some(6), sourceTag2 = Some(7))
    }
  }

  it should "get source tags and values from reorder buffer" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setReorderBuffer(
        destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7), value1 = Some(20), value2 = Some(21))

      c.expectReorderBuffer(rd = Some(1), rs1 = Some(2), rs2 = Some(3))
      c.expectReservationStation(
        destinationTag = Some(5), sourceTag1 = Some(6), sourceTag2 = Some(7), value1 = Some(20), value2 = Some(21))
    }
  }

  it should "understand sd" in {
    test(new DecoderWrapper(0)) { c =>
      // sd x1,10(x2)
      c.initialize(0x00113523.U)
      c.expectReorderBuffer(rd = None, rs1 = Some(2), rs2 = Some(1))
      c.expectReservationStation(immediateOrFunction7 = Some(10))
    }
  }

  it should "understand immediate" in {
    test(new DecoderWrapper(0)) { c =>
      // addi x1,x2,20
      c.initialize(0x01410093.U)
      c.setReorderBuffer(destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7))

      c.expectReorderBuffer(rd = Some(1), rs1 = Some(2), rs2 = None)
      c.expectReservationStation(destinationTag = Some(5), sourceTag1 = Some(6), value2 = Some(20))
    }
  }

  it should "do register bypass" in {
    test(new DecoderWrapper(0, 2)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.setReorderBuffer(destinationTag = 5, sourceTag1 = Some(6), sourceTag2 = Some(7))
      c.setALU(Seq(Some((6, 20)), Some((7, 21))))

      c.expectReorderBuffer(rd = Some(1))
      c.expectReservationStation(destinationTag = Some(5), value1 = Some(20), value2 = Some(21))
    }
  }
}