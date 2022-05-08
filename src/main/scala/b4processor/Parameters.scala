package b4processor

/**
 * プロセッサを生成する際のパラメータ
 *
 * @param tagWidth                   リオーダバッファで使用するタグのビット数
 * @param numberOfDecoders           デコーダの数
 * @param numberOfALUs               ALUの数
 * @param maxRegisterFileCommitCount リオーダバッファからレジスタファイルに1クロックでコミットする命令の数(Max)
 * @param maxLSQ2MemoryinstCount     LSQからメモリに送出する命令の数(max)
 * @param debug                      デバッグ機能を使う
 */
case class Parameters(tagWidth: Int = 7,
                      numberOfDecoders: Int = 2,
                      numberOfALUs: Int = 2,
                      maxRegisterFileCommitCount: Int = 4,
                      maxLSQ2MemoryinstCount: Int = 4,
                      debug: Boolean = false)
