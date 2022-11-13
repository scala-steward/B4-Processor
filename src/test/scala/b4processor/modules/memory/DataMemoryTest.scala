package b4processor.modules.memory

import b4processor.Parameters
import b4processor.structures.memoryAccess.{MemoryAccessInfo, MemoryAccessWidth}
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth._
import b4processor.utils.{DataMemoryValue, InstructionUtil, Tag}
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class DataMemoryTestWrapper(implicit params: Parameters)
    extends DataMemory(instructions = "") {

  def store(
    address: Int,
    data: UInt,
    width: MemoryAccessWidth.Type = DoubleWord
  ): Unit = {
    while (!this.io.dataIn.ready.peekBoolean()) this.clock.step()

    this.io.dataIn.valid.poke(true)
    this.io.dataIn.bits.address.poke(address)
    this.io.dataIn.bits.data.poke(data)
    this.io.dataIn.bits.tag.poke(0)
    this.io.dataIn.bits.accessInfo.poke(
      (new MemoryAccessInfo)
        .Lit(_.accessType -> Store, _.accessWidth -> width, _.signed -> false.B)
    )
    this.clock.step()
    this.io.dataIn.valid.poke(false)
  }

  def load(
    address: Int,
    tag: Int,
    width: MemoryAccessWidth.Type = DoubleWord,
    signed: Boolean = false
  ): Unit = {
    while (!this.io.dataIn.ready.peekBoolean()) this.clock.step()

    this.io.dataIn.bits.address.poke(address)
    this.io.dataIn.bits.data.poke(0)
    this.io.dataIn.bits.tag.poke(tag)
    this.io.dataIn.bits.accessInfo.poke(
      (new MemoryAccessInfo)
        .Lit(_.accessType -> Load, _.accessWidth -> width, _.signed -> false.B)
    )
    this.io.dataIn.valid.poke(true)

    this.clock.step()
    this.io.dataIn.valid.poke(false)
    while (!this.io.dataOut.validAsResult.peekBoolean()) this.clock.step()
  }

  def expectOutput(data: UInt, tag: Int): Unit = {
    this.io.dataOut.validAsResult.expect(true)
    this.io.dataOut.validAsLoadStoreAddress.expect(false)
    this.io.dataOut.value.expect(data)
    this.io.dataOut.tag.expect(Tag(tag))
  }
}

class DataMemoryTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Data Memory"

  implicit val params = Parameters()

  it should "store and load a value" in {
    test(new DataMemoryTestWrapper).withAnnotations(Seq(WriteFstAnnotation)) {
      c =>
        // 0アドレスへのストア
        c.store(0, 123.U)
        // 別アドレスへのストア
        c.store(40, 1000.U)
        // 40アドレスからのロード
        c.load(40, 20)
        c.expectOutput(1000.U, 20)
        // 0からロード
        c.load(0, 10)
        c.expectOutput(123.U, 10)

        c.clock.step(2)
    }
  }

  it should "load and next clock store at the same time" in {
    test(new DataMemoryTestWrapper) { c =>
      // STORE
      c.store(16, 123.U)
      // LOAD
      c.load(16, 20)
      c.expectOutput(123.U, 20)
      // STORE
      c.store(8, 456.U)
      // LOAD
      c.load(8, 25)
      c.expectOutput(456.U, 25)

      c.clock.step(2)
    }
  }

  it should "load a byte value" in {
    test(new DataMemoryTestWrapper).withAnnotations(Seq(WriteFstAnnotation)) {
      c =>
        // 0アドレスへのストア
        c.store(24, "b10000000011".U)
        // 0アドレスからのロード
        c.load(24, 20, Byte)
        c.expectOutput("b00000011".U, 20)

        c.clock.step(2)
    }
  }

  it should "store double-word and get small bytes" in {
    test(new DataMemoryTestWrapper).withAnnotations(Seq(WriteFstAnnotation)) {
      c =>
        // 0アドレスへのストア
        c.store(0, "xFEDCBA9876543210".U)
        // byte単位でロード
        for (
          (v, i) <- Seq(
            "FE",
            "DC",
            "BA",
            "98",
            "76",
            "54",
            "32",
            "10"
          ).reverse.zipWithIndex
        ) {
          c.load(i, 0, Byte)
          c.expectOutput(s"x${v}".U, 0)
        }

        // half-word単位のロード
        for (
          (v, i) <- Seq("FEDC", "BA98", "7654", "3210").reverse.zipWithIndex
        ) {
          c.load(i * 2, 0, HalfWord)
          c.expectOutput(s"x${v}".U, 0)
        }

        // word単位のロード
        for ((v, i) <- Seq("FEDCBA98", "76543210").reverse.zipWithIndex) {
          c.load(i * 4, 0, Word)
          c.expectOutput(s"x${v}".U, 0)
        }
    }
  }

  it should "store small bytes and get double word" in {
    test(new DataMemoryTestWrapper).withAnnotations(Seq(WriteFstAnnotation)) {
      c =>
        // byte単位でストア
        for (
          (v, i) <- Seq(
            "FE",
            "DC",
            "BA",
            "98",
            "76",
            "54",
            "32",
            "10"
          ).reverse.zipWithIndex
        ) {
          c.store(i, s"x${v}".U, Byte)
        }
        c.load(0, 0)
        c.expectOutput(s"xFEDCBA9876543210".U, 0)

        // half-word単位のロード
        for (
          (v, i) <- Seq("FEDC", "BA98", "7654", "3210").reverse.zipWithIndex
        ) {
          c.store(i * 2, s"x${v}".U, HalfWord)
        }
        c.load(0, 0)
        c.expectOutput(s"xFEDCBA9876543210".U, 0)

        // word単位のロード
        for ((v, i) <- Seq("FEDCBA98", "76543210").reverse.zipWithIndex) {
          c.store(i * 4, s"x${v}".U, Word)
        }
        c.load(0, 0)
        c.expectOutput(s"xFEDCBA9876543210".U, 0)
    }
  }
}
