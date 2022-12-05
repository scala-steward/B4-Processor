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
class RegisterFile(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {

    /** デコーダへ */
    val decoders =
      Flipped(Vec(params.decoderPerThread, new Decoder2RegisterFile))

    /** リオーダバッファ */
    val reorderBuffer = Flipped(
      Vec(params.maxRegisterFileCommitCount, new ReorderBuffer2RegisterFile())
    )

    val values = if (params.debug) Some(Output(Vec(32, UInt(64.W)))) else None
  })

  /** レジスタx1~x31 */
  val registers = RegInit(VecInit(Seq.fill(31)(0.U(64.W))))

  for (regIndex <- 1 until 32) {
    // 最新の情報に更新したいのでリオーダバッファから渡されたデータを逆順に見る
    registers(regIndex - 1) := MuxCase(
      registers(regIndex - 1),
      io.reorderBuffer.reverse.map { rb =>
        (rb.valid && rb.bits.destinationRegister === regIndex.U) -> rb.bits.value
      }
    )
  }

  // リオーダバッファからくる信号はすべてtrueにして置く
  for (rb <- io.reorderBuffer)
    rb.ready := true.B

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
    new RegisterFile,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
