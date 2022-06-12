package b4processor.connections

import b4processor.Parameters
import b4processor.modules.decoder.SourceTagInfo
import chisel3._
import chisel3.util._

/**
 * デコーダとLSQをつなぐ
 *
 * @param params パラメータ
 */
class Decoder2LoadStoreQueue(implicit params: Parameters) extends Bundle {
  /** オペコード */
  val opcode = UInt(7.W)
  /** function3 */
  val function3 = UInt(3.W)

  /** アドレスの計算結果とストアデータが格納されるタグ */
  val addressAndLoadResultTag = UInt(params.tagWidth.W)

  /** ストアデータが保存されるタグ */
  val storeDataTag = UInt(params.tagWidth.W)
  /** ストアデータ */
  val storeData = UInt(64.W)
  /** ストアデータの値が有効か */
  val storeDataValid = Bool()

  /** プログラムカウンタ */
  val programCounter = SInt(64.W)
}