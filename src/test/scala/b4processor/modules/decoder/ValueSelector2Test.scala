package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.common.OpcodeFormat
import b4processor.common.OpcodeFormat._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ValueSelector2Wrapper(implicit params: Parameters) extends ValueSelector2 {
  /**
   * 初期化
   *
   * @param sourceTag          ソースタグ
   * @param registerFileValue  レジスタファイルから渡される値
   * @param reorderBufferValue リオーダバッファからの値
   * @param aluBypassValue     ALUからバイパスされてきた値。タプルの1つめの値がdestination tag、2つめがvalue。
   */
  def initalize(sourceTag: Option[Int] = None, registerFileValue: Int = 0, reorderBufferValue: Option[Int] = None, aluBypassValue: Seq[Option[(Int, Int)]] = Seq.fill(params.runParallel)(None), opcodeFormat: OpcodeFormat.Type = R, immediate: Int = 0): Unit = {
    for (i <- aluBypassValue.indices) {
      this.io.executorOutputValue(i).valid.poke(aluBypassValue(i).isDefined.B)
      this.io.executorOutputValue(i).destinationTag.poke(aluBypassValue(i).getOrElse((0, 0))._1.U)
      this.io.executorOutputValue(i).value.poke(aluBypassValue(i).getOrElse((0, 0))._2.U)
    }
    this.io.reorderBufferValue.valid.poke(reorderBufferValue.isDefined)
    this.io.reorderBufferValue.bits.poke(reorderBufferValue.getOrElse(0))
    this.io.registerFileValue.poke(registerFileValue.U)
    this.io.sourceTag.valid.poke(sourceTag.isDefined.B)
    this.io.sourceTag.tag.poke(sourceTag.getOrElse(0).U)
    this.io.immediateValue.poke(immediate.S)
    this.io.opcodeFormat.poke(opcodeFormat)
  }

  def expectValue(value: Option[Int]): Unit = {
    this.io.value.valid.expect(value.isDefined.B, "valueがありませんです")
    if (value.isDefined) {
      this.io.value.bits.expect(value.get.U, "valueの値が間違っています")
    }
  }
}

class ValueSelector2Test extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ValueSelector1"
  implicit val defaultParams = Parameters(runParallel = 0)

  it should "use the register file" in {
    test(new ValueSelector2Wrapper) { c =>
      c.initalize(registerFileValue = 5)
      c.expectValue(Some(5))
    }
  }

  it should "use the reorder buffer" in {
    test(new ValueSelector2Wrapper) { c =>
      c.initalize(sourceTag = Some(3), registerFileValue = 5, reorderBufferValue = Some(6))
      c.expectValue(Some(6))
    }
  }

  it should "use the alu bypass" in {
    test(new ValueSelector2Wrapper()(defaultParams.copy(runParallel = 1))) { c =>
      c.initalize(sourceTag = Some(3), registerFileValue = 5, aluBypassValue = Seq(Some((3, 12))))
      c.expectValue(Some(12))
    }
  }

  it should "use multiple alu bypasses" in {
    test(new ValueSelector2Wrapper()(defaultParams.copy(runParallel = 4))) { c =>
      c.initalize(sourceTag = Some(3), registerFileValue = 5, aluBypassValue = Seq(Some((1, 10)), Some(2, 11), Some(3, 12), Some(4, 13)))
      c.expectValue(Some(12))
    }
  }

  it should "not have any value" in {
    test(new ValueSelector2Wrapper) { c =>
      c.initalize(sourceTag = Some(3), registerFileValue = 5)
      c.expectValue(None)
    }
  }

  it should "use immediate" in {
    test(new ValueSelector2Wrapper) { c =>
      c.initalize(immediate = 9, opcodeFormat = I)
      c.expectValue(Some(9))
    }
  }

  it should "not use immediate" in {
    test(new ValueSelector2Wrapper) { c =>
      c.initalize(registerFileValue = 5, immediate = 9, opcodeFormat = R)
      c.expectValue(Some(5))
    }
  }
}
