package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessInfo
import b4processor.utils.Tag
import chisel3._

/** LSQのエントリ
  *
  * @param params
  *   パラメータ
  */
class LoadStoreQueueEntry(implicit params: Parameters) extends Bundle {

  /** エントリが有効である */
  val valid = Bool()

  /** 命令がリオーダバッファでコミットされたか */
  val readyReorderSign = Bool()

  /** メモリアクセスの情報 */
  val info = new MemoryAccessInfo

  /** 命令自体を識別するためのタグ(Destination Tag) */
  val addressAndLoadResultTag = new Tag

  /** アドレス値 */
  val address = SInt(64.W)

  /** アドレス値が有効である */
  val addressValid = Bool()

  /** ストアに使用するデータが格納されるタグ(SourceRegister2 Tag) */
  val storeDataTag = new Tag

  /** ストアデータ */
  val storeData = UInt(64.W)

  /** ストアデータが有効である */
  val storeDataValid = Bool()
}

object LoadStoreQueueEntry {
  def validEntry(
    accessInfo: MemoryAccessInfo,
    addressAndStoreResultTag: UInt,
    storeDataTag: UInt,
    storeData: UInt,
    storeDataValid: Bool
  )(implicit params: Parameters): LoadStoreQueueEntry = {
    val entry = LoadStoreQueueEntry.default
    entry.valid := true.B

    entry.info := accessInfo

    entry.addressAndLoadResultTag := addressAndStoreResultTag
    entry.address := 0.S
    entry.addressValid := false.B

    entry.storeDataTag := storeDataTag
    entry.storeData := storeData
    entry.storeDataValid := storeDataValid

    entry
  }

  def default(implicit params: Parameters): LoadStoreQueueEntry = {
    val entry = Wire(new LoadStoreQueueEntry)
    entry.valid := false.B
    entry.readyReorderSign := false.B

    entry.info := DontCare

    entry.addressAndLoadResultTag := 0.U
    entry.address := 0.S
    entry.addressValid := false.B

    entry.storeDataTag := 0.U
    entry.storeData := 0.U
    entry.storeDataValid := false.B

    entry
  }
}
