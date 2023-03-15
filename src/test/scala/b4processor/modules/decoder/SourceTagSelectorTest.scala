package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SourceTagSelectorWrapper(implicit params: Parameters)
    extends SourceTagSelector {
  def initialize(reorderBufferValue: Option[Int]): Unit = {
    initializeReorderBuffer(reorderBufferValue)
  }

  def initializeReorderBuffer(value: Option[Int]): Unit = {
    this.io.reorderBufferDestinationTag.valid.poke(value.isDefined.B)
    this.io.reorderBufferDestinationTag.bits.poke(Tag(0, value.getOrElse(0)))
  }

  def expect(value: Option[Int]): Unit = {
    this.io.sourceTag.valid.expect(value.isDefined.B, "source tagが出力されていません")
    if (value.isDefined) {
      this.io.sourceTag.tag.expect(Tag(0, value.get), "source tagの値が間違っています")
    }
  }
}

class SourceTagSelectorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SourceTagSelector"

  implicit val defaultParams = Parameters()

  it should "select the destination tag from reorder buffer" in {
    test(new SourceTagSelectorWrapper) { c =>
      c.initialize(Some(2))
      c.expect(Some(2))
    }
  }

  it should "output no source tag" in {
    test(new SourceTagSelectorWrapper) { c =>
      c.initialize(None)
      c.expect(None)
    }
  }
}
