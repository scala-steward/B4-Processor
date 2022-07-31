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
    val decoders = Flipped(Vec(params.runParallel, new Decoder2RegisterFile))

    /** リオーダバッファ */
    val reorderBuffer = Flipped(
      Vec(
        params.maxRegisterFileCommitCount,
        Valid(new ReorderBuffer2RegisterFile())
      )
    )

    val values = if (params.debug) Some(Output(Vec(31, UInt(64.W)))) else None
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

  // それぞれのデコーダへの信号
  for (dec <- io.decoders) {
    // ソースレジスタが0ならば0それ以外ならばレジスタから
    dec.value1 := MuxLookup(
      dec.sourceRegister1,
      0.U,
      (1 to 31).map { i => i.U -> registers(i - 1) }
    )
    dec.value2 := MuxLookup(
      dec.sourceRegister2,
      0.U,
      (1 to 31).map { i => i.U -> registers(i - 1) }
    )
  }

  // デバッグ用信号
  if (params.debug)
    io.values.get := registers
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
