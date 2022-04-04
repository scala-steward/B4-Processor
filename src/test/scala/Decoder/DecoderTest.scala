package Decoder

import org.scalatest.flatspec.AnyFlatSpec
import chisel3._
import chisel3.util._
import chiseltest._


/**
 * デコーダをテストしやすくするためにラップしたもの
 *
 * @param instruction_offset 同時に扱う命令のうちいくつ目の命令を担当するか
 */
class DecoderWrapper(instruction_offset: Int) extends Decoder(instruction_offset) {
  def initialize(instruction: UInt): Unit = {
    this.io.imem.bits.program_counter.poke(0.U)
    this.io.imem.bits.instruction.poke(instruction)
    this.io.imem.valid.poke(true.B)
    this.io.reorderBuffer.destination.destinationTag.poke(0.U)
    this.io.reorderBuffer.source1.matchingTag.valid.poke(true.B)
    this.io.reorderBuffer.source1.matchingTag.bits.poke(0.U)
    this.io.reorderBuffer.source1.value.valid.poke(false.B)
    this.io.reorderBuffer.source1.value.bits.poke(0.B)
    this.io.reorderBuffer.source2.matchingTag.valid.poke(true.B)
    this.io.reorderBuffer.source2.matchingTag.bits.poke(0.U)
    this.io.reorderBuffer.source2.value.valid.poke(false.B)
    this.io.reorderBuffer.source2.value.bits.poke(0.B)
  }

  def expect_reorder_buffer(rd: Option[Int], rs1: Int, rs2: Option[Int]): Unit = {
    // check rd
    rd match {
      case None => {
        this.io.reorderBuffer.destination.destinationRegister.valid.expect(false.B, "rd should not be valid")
      }
      case Some(value) => {
        this.io.reorderBuffer.destination.destinationRegister.valid.expect(true.B, "rd should be valid")
        this.io.reorderBuffer.destination.destinationRegister.bits.expect(value.U, "rd value doesn't match")
      }
    }

    // check rs1
    this.io.reorderBuffer.source1.sourceRegister.expect(rs1.U, "rs1 doesn't match")

    // check rs2
    if (rs2.isDefined) {
      this.io.reorderBuffer.source2.sourceRegister.expect(rs2.get.U, "rs2 doesn't match")
    }
  }

  def expect_reservation_station(): Unit = {
  }
}

/**
 * デコーダのテスト
 */
class DecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Decoder"

  it should "pass rs1 rs2 rd to reorder buffer" in {
    test(new DecoderWrapper(0)) { c =>
      c.initialize("b0000000_00011_00010_000_00001_0000000".U)
      c.expect_reorder_buffer(Some(1), 2, Some(3))
    }
  }

  it should "understand R type instruction" in {
    test(new DecoderWrapper(0)) { c =>
      // add x1,x2,x3
      c.initialize(0x003100b3.U)
      c.expect_reorder_buffer(Some(1), 2, Some(3))
    }
  }

  // TODO: これも確認
//  it should "understand sd" in {
//    test(new DecoderWrapper(0)) { c =>
//      // sd x1,10(x2)
//      c.initialize(0x00113523.U)
//      c.expect_reorder_buffer(None, 1, Some(2))
//    }
//  }

  it should "understand immediate" in {
    test(new DecoderWrapper(0)) { c =>
      // addi x1,x2,20
      c.initialize(0x01410093.U)
      c.expect_reorder_buffer(Some(1), 2, None)
    }
  }
}
