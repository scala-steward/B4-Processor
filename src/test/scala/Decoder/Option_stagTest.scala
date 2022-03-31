package Decoder

import org.scalatest.flatspec.AnyFlatSpec
import chisel3._
import chisel3.util._
import chiseltest._

class Option_stagTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "OptionStag"

  it should "select the destination tag from previous instructions (with input from reorder buffer)" in {
    test(new Option_stag()) {c =>
      c.io.before_dtag.valid.poke(true.B)
      c.io.before_dtag.bits.poke(1.U)
      c.io.reorder_buffer_dtag.valid.poke(true.B)
      c.io.reorder_buffer_dtag.bits.poke(2.U)
      c.io.stag.ready.poke(true.B)

      c.io.stag.valid.expect(true.B)
      c.io.stag.bits.expect(1.U)
    }
  }

  it should "select the destination tag from previous instructions (with no input from reorder buffer)" in {
    test(new Option_stag()) {c =>
      c.io.before_dtag.valid.poke(true.B)
      c.io.before_dtag.bits.poke(1.U)
      c.io.reorder_buffer_dtag.valid.poke(false.B)
      c.io.reorder_buffer_dtag.bits.poke(2.U)
      c.io.stag.ready.poke(true.B)

      c.io.stag.valid.expect(true.B)
      c.io.stag.bits.expect(1.U)
    }
  }

  it should "select the destination tag from reorder buffer" in {
    test(new Option_stag()) {c =>
      c.io.before_dtag.valid.poke(false.B)
      c.io.before_dtag.bits.poke(1.U)
      c.io.reorder_buffer_dtag.valid.poke(true.B)
      c.io.reorder_buffer_dtag.bits.poke(2.U)

      c.io.stag.valid.expect(true.B)
      c.io.stag.bits.expect(2.U)
    }
  }

  it should "output no stag" in {
    test(new Option_stag()) {c =>
      c.io.before_dtag.valid.poke(false.B)
      c.io.before_dtag.bits.poke(1.U)
      c.io.reorder_buffer_dtag.valid.poke(false.B)
      c.io.reorder_buffer_dtag.bits.poke(2.U)

      c.io.stag.valid.expect(false.B)
    }
  }
}
