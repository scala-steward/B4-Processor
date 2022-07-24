package b4processor.connections

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessInfo
import chisel3._

/** デコーダとLSQをつなぐ
  *
  * @param params
  *   パラメータ
  */
class Decoder2LoadStoreQueue(implicit params: Parameters) extends Bundle {

  /** メモリアクセスの情報 */
  val accessInfo = new MemoryAccessInfo()

  /** 命令自体を識別するためのタグ(Destination Tag) */
  val addressAndLoadResultTag = UInt(params.tagWidth.W)

  /** ストアに使用するデータが格納されるタグ(SourceRegister2 Tag) */
  val storeDataTag = UInt(params.tagWidth.W)

  /** ストアデータ */
  val storeData = UInt(64.W)

  /** ストアデータの値が有効か */
  val storeDataValid = Bool()
}
