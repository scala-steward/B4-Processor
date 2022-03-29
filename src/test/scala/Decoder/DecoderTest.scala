package Decoder

import org.scalatest.flatspec.AnyFlatSpec
import chisel3._
import chisel3.util._
import chiseltest._

class DecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Decoder"

  it should "pass this dummy test" in {
    assert(1 + 1 === 2)
  }

  it should "pass rs1 rs2 rd to reorder buffer" in {
    test(new Decoder(0)) { c =>
      c.io.imem.program_counter.poke(0.U)
      c.io.imem.instruction.poke("b0000000_00011_00010_000_00001_0000000".U)
      c.io.reorder_buffer.destination.destination_tag.poke(0.U)
      c.io.reorder_buffer.source1.matching_tag.valid.poke(true.B)
      c.io.reorder_buffer.source1.matching_tag.bits.poke(0.U)
      c.io.reorder_buffer.source1.value.valid.poke(false.B)
      c.io.reorder_buffer.source1.value.bits.poke(0.B)
      c.io.reorder_buffer.source2.matching_tag.valid.poke(true.B)
      c.io.reorder_buffer.source2.matching_tag.bits.poke(0.U)
      c.io.reorder_buffer.source2.value.valid.poke(false.B)
      c.io.reorder_buffer.source2.value.bits.poke(0.B)

      c.io.reorder_buffer.destination.register_destination.expect(1.U)
      c.io.reorder_buffer.source1.register_source.expect(2.U)
      c.io.reorder_buffer.source2.register_source.expect(3.U)
    }
  }
}
