package b4processor.modules.memory

import b4processor.Parameters
import b4processor.structures.memoryAccess.{MemoryAccessInfo, MemoryAccessWidth}
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth._
import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class DataMemoryInterfaceTestWrapper(implicit params: Parameters)
    extends DataMemoryInterface() {

  def load(
    address: Int,
    tag: Int,
    width: MemoryAccessWidth.Type = DoubleWord
  ): Unit = {
    this.io.dataIn.valid.poke(true)
    this.io.dataIn.bits.address.poke(address)
    this.io.dataIn.bits.tag.poke(tag)
    this.io.dataIn.bits.data.poke(0)
    this.io.dataIn.bits.accessInfo.poke(
      (new MemoryAccessInfo)
        .Lit(_.accessType -> Load, _.accessWidth -> width, _.signed -> false.B)
    )
    this.clock.step()
    this.io.dataIn.valid.poke(false)
  }

  def store(
    address: Int,
    data: UInt,
    width: MemoryAccessWidth.Type = DoubleWord
  ): Unit = {
    this.io.dataIn.valid.poke(true)
    this.io.dataIn.bits.address.poke(address)
    this.io.dataIn.bits.tag.poke(0)
    this.io.dataIn.bits.data.poke(data)
    this.io.dataIn.bits.accessInfo.poke(
      (new MemoryAccessInfo)
        .Lit(_.accessType -> Store, _.accessWidth -> width, _.signed -> false.B)
    )
    this.clock.step()
    this.io.dataIn.valid.poke(false)
  }

  def expectOutput(data: UInt, tag: Int): Unit = {
    this.io.dataOut.validAsResult.expect(true)
    this.io.dataOut.validAsLoadStoreAddress.expect(false)
    this.io.dataOut.value.expect(data)
    this.io.dataOut.tag.expect(tag)
  }
}

class DataMemoryInterfaceTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Data Memory Interface"

  implicit val params = Parameters()

  it should "store and load" in {
    test(new DataMemoryInterfaceTestWrapper).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        // 100アドレスへのストア

        c.store(100, 10.U)
        c.io.master.writeAddr.ready.poke(true)
        c.io.master.writeData.ready.poke(true)
        c.clock.step(1)
        // 0からロード
        c.load(100, 5)

        c.clock.step(5)


    }
  }
}
