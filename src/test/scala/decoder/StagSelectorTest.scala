package decoder

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class StagSelectorWrapper(instruction_offset: Int) extends StagSelector(instruction_offset) {
  def initialize(dtags: Seq[Option[Int]], reorderBufferValue: Option[Int]): Unit = {
    this.io.stag.ready.poke(true.B)
    initializeBeforeDtag(dtags)
    initializeReorderBuffer(reorderBufferValue)
  }

  def initializeBeforeDtag(dtags: Seq[Option[Int]]): Unit = {
    for (i <- dtags.indices) {
      this.io.beforeDtag(i).valid.poke(dtags(i).isDefined.B)
      this.io.beforeDtag(i).bits.poke(dtags(i).getOrElse(0).U)
    }
  }

  def initializeReorderBuffer(value: Option[Int]): Unit = {
    this.io.reorderBufferDtag.valid.poke(value.isDefined.B)
    this.io.reorderBufferDtag.bits.poke(value.getOrElse(0))
  }

  def expect(value: Option[Int]): Unit = {
    this.io.stag.valid.expect(value.isDefined.B, "stagが出力されていません")
    if (value.isDefined) {
      this.io.stag.bits.expect(value.get.U, "stagの値が間違っています")
    }
  }
}

class StagSelectorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "StagSelector"

  it should "select the destination tag from previous instructions (with input from reorder buffer)" in {
    test(new StagSelectorWrapper(1)) { c =>
      c.initialize(Seq(Some(1)), Some(2))
      c.expect(Some(1))
    }
  }

  it should "select the destination tag from previous instructions (with no input from reorder buffer)" in {
    test(new StagSelectorWrapper(1)) { c =>
      c.initialize(Seq(Some(1)), None)
      c.expect(Some(1))
    }
  }

  it should "select the destination tag from reorder buffer" in {
    test(new StagSelectorWrapper(1)) { c =>
      c.initialize(Seq(None), Some(2))
      c.expect(Some(2))
    }
  }

  it should "output no stag" in {
    test(new StagSelectorWrapper(1)) { c =>
      c.initialize(Seq(None), None)
      c.expect(None)
    }
  }

  it should "choose the first dtag from beforeDtag" in {
    test(new StagSelectorWrapper(4)) { c =>
      c.initialize(Seq(Some(1), Some(2), Some(3), Some(4)), None)
      c.expect(Some(1))
    }
  }

  it should "choose the first valid dtag from beforeDtag" in {
    test(new StagSelectorWrapper(4)) { c =>
      c.initialize(Seq(None, None, Some(3), Some(4)), None)
      c.expect(Some(3))
    }
  }
}
