package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.InstructionCache2Fetch
import b4processor.modules.memory.InstructionMemory
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/** メモリをキャッシュを含んだラッパー */
class MemoryAndCache(memoryInit: => Seq[UInt])(implicit params: Parameters) extends Module {
  val io = IO(Vec(params.runParallel, new InstructionCache2Fetch))

  val cache = Module(new InstructionMemoryCache)
  val memory = Module(new InstructionMemory(memoryInit))

  io <> cache.io.fetch
  cache.io.memory <> memory.io
}

class InstructionMemoryCacheTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Instruction Cache"
  implicit val defaultParams = Parameters(fetchWidth = 2)

  /** 命令を読み込む */
  it should "load memory" in {
    test(new MemoryAndCache((0 until 100).map(_.U(8.W)))) { c =>
      c.io(0).address.poke(0)
      c.io(0).output.valid.expect(true)
      c.io(0).output.bits.expect("x03020100".U)

      c.io(1).address.poke(4)
      c.io(1).output.valid.expect(true)
      c.io(1).output.bits.expect("x07060504".U)

      c.io(1).address.poke(8)
      c.io(1).output.valid.expect(false)
    }
  }
}
