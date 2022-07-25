package b4processor.modules.decoder

import b4processor.Parameters
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SourceTagSelectorWrapper(instruction_offset: Int)(implicit
  params: Parameters
) extends SourceTagSelector(instruction_offset) {
  def initialize(
    destinationTags: Seq[Option[Int]],
    reorderBufferValue: Option[Int]
  ): Unit = {
    initializeBeforeDestinationTag(destinationTags)
    initializeReorderBuffer(reorderBufferValue)
  }

  def initializeBeforeDestinationTag(destinationTag: Seq[Option[Int]]): Unit = {
    for (i <- destinationTag.indices) {
      this.io.beforeDestinationTag(i).valid.poke(destinationTag(i).isDefined.B)
      this.io
        .beforeDestinationTag(i)
        .bits
        .poke(destinationTag(i).getOrElse(0).U)
    }
  }

  def initializeReorderBuffer(value: Option[Int]): Unit = {
    this.io.reorderBufferDestinationTag.valid.poke(value.isDefined.B)
    this.io.reorderBufferDestinationTag.bits.poke(value.getOrElse(0))
  }

  def expect(value: Option[Int]): Unit = {
    this.io.sourceTag.valid.expect(value.isDefined.B, "source tagが出力されていません")
    if (value.isDefined) {
      this.io.sourceTag.tag.expect(value.get.U, "source tagの値が間違っています")
    }
  }
}

class SourceTagSelectorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SourceTagSelector"

  implicit val defaultParams = Parameters()

  it should "select the destination tag from previous instructions (with input from reorder buffer)" in {
    test(new SourceTagSelectorWrapper(1)) { c =>
      c.initialize(Seq(Some(1)), Some(2))
      c.expect(Some(1))
    }
  }

  it should "select the destination tag from previous instructions (with no input from reorder buffer)" in {
    test(new SourceTagSelectorWrapper(1)) { c =>
      c.initialize(Seq(Some(1)), None)
      c.expect(Some(1))
    }
  }

  it should "select the destination tag from reorder buffer" in {
    test(new SourceTagSelectorWrapper(1)) { c =>
      c.initialize(Seq(None), Some(2))
      c.expect(Some(2))
    }
  }

  it should "output no source tag" in {
    test(new SourceTagSelectorWrapper(1)) { c =>
      c.initialize(Seq(None), None)
      c.expect(None)
    }
  }

  it should "choose the last destination tag from beforeDestinationTag" in {
    test(new SourceTagSelectorWrapper(4)) { c =>
      c.initialize(Seq(Some(1), Some(2), Some(3), Some(4)), None)
      c.expect(Some(4))
    }
  }

  it should "choose the last valid destination tag from beforeDestinationTag" in {
    test(new SourceTagSelectorWrapper(4)) { c =>
      c.initialize(Seq(None, Some(2), Some(3), None), None)
      c.expect(Some(3))
    }
  }
}
