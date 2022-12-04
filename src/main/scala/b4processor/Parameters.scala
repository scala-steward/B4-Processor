package b4processor

import scala.math.pow

/** プロセッサを生成する際のパラメータ
  *
  * @param tagWidth
  *   リオーダバッファで使用するタグのビット数
  * @param loadStoreQueueIndexWidth
  *   ロードストアキューに使うインデックスのビット幅
  * @param runParallel
  *   同時実行数
  * @param maxRegisterFileCommitCount
  *   リオーダバッファからレジスタファイルに1クロックでコミットする命令の数(Max)
  * @param maxDataMemoryCommitCount
  *   メモリバッファに出力する最大数
  * @param debug
  *   デバッグ機能を使う
  * @param fetchWidth
  *   命令フェッチ時にメモリから取出す命令数
  * @param branchPredictionWidth
  *   分岐予測で使う下位ビット数
  * @param instructionStart
  *   プログラムカウンタの初期値
  * @param dataMemorySize
  *   データメモリのサイズ
  */
case class Parameters(
  tagWidth: Int = 4,
  reservationStationWidth: Int = 3,
  loadStoreQueueIndexWidth: Int = 3,
  loadStoreQueueCheckLength: Int = 3,
  decoderPerThread: Int = 2,
  threads: Int = 2,
  maxRegisterFileCommitCount: Int = 4,
  maxDataMemoryCommitCount: Int = 4,
  fetchWidth: Int = 4,
  branchPredictionWidth: Int = 4,
  instructionStart: Long = 0x4000_0000L,
  ramStart: Long = 0x8000_0000L,
  debug: Boolean = false,
  dataMemorySize: Long = pow(2, 10).toInt
)
