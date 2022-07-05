package b4processor.modules.lsq

import b4processor.Parameters
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

  /** オペコード */
  val isLoad = Bool()

  /** function3 */
  val function3 = UInt(3.W)

  /** 命令自体を識別するためのタグ(Destination Tag) */
  val addressAndLoadResultTag = UInt(params.tagWidth.W)

  /** アドレス値 */
  val address = SInt(64.W)

  /** アドレス値が有効である */
  val addressValid = Bool()

  /** ストアに使用するデータが格納されるタグ(SourceRegister2 Tag) */
  val storeDataTag = UInt(params.tagWidth.W)

  /** ストアデータ */
  val storeData = UInt(64.W)

  /** ストアデータが有効である */
  val storeDataValid = Bool()
}

object LoadStoreQueueEntry {
  def validEntry(
    isLoad: Bool,
    function3: UInt,
    addressAndStoreResultTag: UInt,
    storeDataTag: UInt,
    storeData: UInt,
    storeDataValid: Bool
  )(implicit params: Parameters): LoadStoreQueueEntry = {
    val entry = LoadStoreQueueEntry.default
    entry.valid := true.B

    entry.isLoad := isLoad
    entry.function3 := function3

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

    entry.isLoad := false.B
    entry.function3 := 0.U

    entry.addressAndLoadResultTag := 0.U
    entry.address := 0.S
    entry.addressValid := false.B

    entry.storeDataTag := 0.U
    entry.storeData := 0.U
    entry.storeDataValid := false.B

    entry
  }
}
