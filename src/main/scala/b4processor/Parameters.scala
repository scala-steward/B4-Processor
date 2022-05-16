package b4processor

/**
 * プロセッサを生成する際のパラメータ
 *
 * @param tagWidth                   リオーダバッファで使用するタグのビット数
 * @param runParallel                同時実行数
 * @param maxRegisterFileCommitCount リオーダバッファからレジスタファイルに1クロックでコミットする命令の数(Max)
 * @param maxLSQ2MemoryInstCount     LSQからメモリに送出する命令の数(max)
 * @param debug                      デバッグ機能を使う
 * @param fetchWidth                 命令フェッチ時にメモリから取出す命令数
 * @param branchPredictionWidth      分岐予測で使う下位ビット数
 * @param pcInit                     プログラムカウンタの初期値
 */
case class Parameters(tagWidth: Int = 6,
                      runParallel: Int = 2,
                      maxRegisterFileCommitCount: Int = 4,
                      fetchWidth: Int = 2,
                      branchPredictionWidth: Int = 4,
                      pcInit: Int = 0x0,
                      maxLSQ2MemoryInstCount: Int = 4,
                      debug: Boolean = false)