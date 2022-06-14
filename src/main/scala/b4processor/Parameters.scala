package b4processor

/**
 * プロセッサを生成する際のパラメータ
 *
 * @param tagWidth                   リオーダバッファで使用するタグのビット数
 * @param runParallel                同時実行数
 * @param maxRegisterFileCommitCount リオーダバッファからレジスタファイルに1クロックでコミットする命令の数(Max)
 * @param debug                      デバッグ機能を使う
 * @param fetchWidth                 命令フェッチ時にメモリから取出す命令数
 * @param branchPredictionWidth      分岐予測で使う下位ビット数
 * @param instructionStart           プログラムカウンタの初期値
 */
case class Parameters(tagWidth: Int = 6,
                      runParallel: Int = 2,
                      maxRegisterFileCommitCount: Int = 4,
                      fetchWidth: Int = 4,
                      branchPredictionWidth: Int = 4,
                      instructionStart: Long = 0x4000_0000,
                      ramStart: Long = 0x8000_0000,
                      debug: Boolean = false)