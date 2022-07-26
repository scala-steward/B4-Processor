package b4processor.modules.branchprediction

import b4processor.Parameters
import chiseltest._
import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec

class BranchBufferWrapper(implicit params: Parameters) extends BranchBuffer {
  def initialize(): Unit = {
    setFetchBranches()
    setCollectedBranchOutputs()
  }

  def addBranch(values: BigInt*): Unit = {
    for (vs <- values.grouped(params.runParallel)) {
      val vsc = (0 until params.runParallel).map(i => vs.lift(i))
      setFetchBranches(vsc)
      this.clock.step()
    }
  }

  def setFetchBranches(
                        values: Seq[Option[BigInt]] = Seq.fill(params.runParallel)(None)
                      ): Unit = {
    assert(values.length == params.runParallel)
    for ((f, v) <- io.fetch.branches.zip(values)) {
      f.valid.poke(v.isDefined)
      f.address.poke(0)
      if (v.isDefined) {
        f.address.poke(v.get)
      }
    }
  }

  def emitOutputs(values: (BigInt, Int)*): Unit = {
    assert(values.length <= params.runParallel)
    val vs = values
    val vsc =
      (0 until params.runParallel).map(i => vs.lift(i))
    setCollectedBranchOutputs(vsc)
    this.clock.step()
  }

  def setCollectedBranchOutputs(
                                 values: Seq[Option[(BigInt, Int)]] = Seq.fill(params.runParallel)(None)
                               ): Unit = {
    assert(values.length == params.runParallel)
    for ((b, v) <- io.branchOutput.addresses.zip(values)) {
      b.valid.poke(v.isDefined)
      b.address.poke(0)
      b.branchID.poke(0)
      if (v.isDefined) {
        b.address.poke(v.get._1)
        b.branchID.poke(v.get._2)
      }
    }
  }

  def expectCorrect(branchID: Int): Unit = {
    this.io.reorderBuffer.valid.expect(true)
    this.io.reorderBuffer.bits.correct.expect(true)
    this.io.reorderBuffer.bits.BranchID.expect(branchID)

    this.io.fetch.changeAddress.valid.expect(false)
  }

  def expectWrong(branchID: Int, address: BigInt): Unit = {
    this.io.reorderBuffer.valid.expect(true)
    this.io.reorderBuffer.bits.correct.expect(false)
    this.io.reorderBuffer.bits.BranchID.expect(branchID)

    this.io.fetch.changeAddress.valid.expect(true)
    this.io.fetch.changeAddress.bits.expect(address)

    for (f <- this.io.fetch.branches) {
      f.ready.expect(false)
    }
  }
}

class BranchBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BranchBuffer"
  implicit val params = Parameters(runParallel = 1)

  it should "add branch and say correct" in {
    test(new BranchBufferWrapper()).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        c.initialize()
        c.addBranch(10, 20)
        c.emitOutputs((10, 0))
        c.expectCorrect(0)
        c.emitOutputs((30, 1))
        c.expectWrong(1, 30)
    }
  }
}
