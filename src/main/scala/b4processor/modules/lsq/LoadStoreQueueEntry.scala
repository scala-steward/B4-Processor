package b4processor.modules.lsq

import b4processor.Parameters
import chisel3._

/**
 * LSQのエントリ
 *
 * @param params パラメータ
 */
class LoadStoreQueueEntry(implicit params: Parameters) extends Bundle {
  /** エントリが有効である FIXME　命令実効済か否か？ */
  val valid = Bool()
  /** FIXME これが何に使われているかわからない */
  val readyReorderSign = Bool()

  // ロードストアの判別用？これより減らせるのでは？ FIXME
  /** オペコード */
  val opcode = UInt(7.W)
  /** function3 */
  val function3 = UInt(3.W)


  /** アドレスの計算結果とストアデータが格納されるタグ */
  val addressAndLoadResultTag = UInt(params.tagWidth.W)
  /** アドレス値 */
  val address = SInt(64.W)
  /** アドレス値が有効である */
  val addressValid = Bool()

  /** ストアに使用するデータが格納されるタグ */
  val storeDataTag = UInt(params.tagWidth.W)
  /** ストアデータ */
  val storeData = UInt(64.W)
  /** ストアデータが有効である */
  val storeDataValid = Bool()

  /** プログラムカウンタ */
  val programCounter = SInt(64.W)
}

object LoadStoreQueueEntry {
  def validEntry(opcode: UInt, function3: UInt,
                 addressAndStoreResultTag: UInt,
                 storeDataTag: UInt, storeData: UInt, storeDataValid: Bool,
                 programCounter: SInt)
                (implicit params: Parameters): LoadStoreQueueEntry = {
    val entry = LoadStoreQueueEntry.default
    entry.valid := true.B

    entry.opcode := opcode
    entry.function3 := function3

    entry.addressAndLoadResultTag := addressAndStoreResultTag
    entry.address := 0.S
    entry.addressValid := false.B

    entry.storeDataTag := storeDataTag
    entry.storeData := storeData
    entry.storeDataValid := storeDataValid

    entry.programCounter := programCounter
    entry
  }

  def default(implicit params: Parameters): LoadStoreQueueEntry = {
    val entry = Wire(new LoadStoreQueueEntry)
    entry.valid := false.B
    entry.readyReorderSign := false.B

    entry.opcode := 0.U
    entry.function3 := 0.U

    entry.addressAndLoadResultTag := 0.U
    entry.address := 0.S
    entry.addressValid := false.B

    entry.storeDataTag := 0.U
    entry.storeData := 0.U
    entry.storeDataValid := false.B

    entry.programCounter := 0.S
    entry
  }
}