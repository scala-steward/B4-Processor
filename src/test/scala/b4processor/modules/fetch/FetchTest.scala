package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.Fetch2BranchPrediction
import b4processor.modules.cache.InstructionMemoryCache
import b4processor.modules.memory.InstructionMemory
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FetchWrapper(memoryInit: => Seq[UInt])(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val prediction = Flipped(Vec(params.numberOfDecoders, new Fetch2BranchPrediction))
  })

  val fetch = Module(new Fetch)
  val cache = Module(new InstructionMemoryCache)
  val memory = Module(new InstructionMemory(memoryInit))

  cache.io.fetch <> fetch.io.cache
  cache.io.memory <> memory.io

  fetch.io.decoders.foreach(_.ready := true.B)

  this.setPrediction(Seq.fill(params.numberOfDecoders)(false))

  def setPrediction(values: Seq[Boolean]): Unit = {
    for (i <- 0 until params.numberOfDecoders) {
      this.fetch.io.prediction(i).prediction.poke(values(i))
    }
  }
}

class FetchTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Fetch"
  implicit val defaultParams = Parameters()

  it should "aaaa" in {
    test(new FetchWrapper((0 until 100).map(_.U(8.W)))) { c =>
    }
  }
}
