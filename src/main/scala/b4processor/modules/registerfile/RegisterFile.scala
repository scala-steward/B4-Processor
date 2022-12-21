package b4processor.modules.registerfile

import b4processor.Parameters
import b4processor.connections.{
  Decoder2RegisterFile,
  ReorderBuffer2RegisterFile
}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

/** レジスタファイル
  *
  * @param params
  *   パラメータ
  */
class RegisterFile(threadId: Int)(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {

    /** デコーダへ */
    val decoders =
      Flipped(Vec(params.decoderPerThread, new Decoder2RegisterFile))

    /** リオーダバッファ */
    val reorderBuffer = Flipped(
      Vec(
        params.maxRegisterFileCommitCount,
        Valid(new ReorderBuffer2RegisterFile())
      )
    )

    val values = if (params.debug) Some(Output(Vec(32, UInt(64.W)))) else None
  })

  /** レジスタx1~x31 tpのみthreadIdで初期化
    */
  val registers = RegInit(
    VecInit(
      (1 until 32)
        .map(n => if (n == 4 /* tp */ ) threadId.U(64.W) else 0.U(64.W))
    )
  )

  for (rb <- io.reorderBuffer.reverse) {
    when(rb.valid) {
      registers(rb.bits.destinationRegister - 1.U) := rb.bits.value
    }
  }

  // それぞれのデコーダへの信号
  for (dec <- io.decoders) {
    // ソースレジスタが0ならば0それ以外ならばレジスタから
    dec.value1 := Mux(
      dec.sourceRegister1 === 0.U,
      0.U,
      registers(dec.sourceRegister1 - 1.U)
    )
    dec.value2 := Mux(
      dec.sourceRegister2 === 0.U,
      0.U,
      registers(dec.sourceRegister2 - 1.U)
    )
  }

  // デバッグ用信号
  if (params.debug) {
    io.values.get(0) := 0.U
    for (i <- 0 until 31)
      io.values.get(i + 1) := registers(i)
  }
}

object RegisterFile extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(
    new RegisterFile(0),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
