package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.{InstructionCache2Fetch, InstructionMemory2Cache}
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.stage.ChiselStage
import chisel3.util._

/** 命令キャッシュモジュール
  *
  * このモジュールは正確にはキャッシュとしては機能していないが、命令メモリとフェッチの間を取り持つ役割を持つ
  */
class InstructionMemoryCache(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {

    /** フェッチ */
    val fetch = Vec(params.runParallel, new InstructionCache2Fetch)

    /** 命令メモリ */
    val memory = Flipped(new InstructionMemory2Cache)
  })

  /** フェッチから要求された最初のアドレス */
  val baseAddress = io.fetch(0).address

  io.memory.address := baseAddress
  io.fetch(0).output.bits := io.memory.output(0)
  io.fetch(0).output.valid := true.B

  for (i <- 1 until params.runParallel) {
    // 命令メモリから取得した幅の中に要求したアドレスがあれば渡す
    io.fetch(i).output := MuxCase(
      Valid(UInt(32.W)).Lit(_.valid -> false.B, _.bits -> 0.U),
      io.memory.output.zipWithIndex.map { case (m, index) =>
        (io.fetch(i).address === baseAddress + (4 * index).S) -> {
          val w = Wire(Valid(UInt(32.W)))
          w.valid := true.B
          w.bits := m
          w
        }
      }
    )
  }
}

object InstructionMemoryCache extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(
    new InstructionMemoryCache(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
