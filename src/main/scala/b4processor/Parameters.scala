package b4processor

/**
 * プロセッサを生成する際のパラメータ
 *
 * @param tagWidth                   リオーダバッファで使用するタグのビット数
 * @param numberOfDecoders           デコーダの数
 * @param numberOfALUs               ALUの数
 * @param maxRegisterFileCommitCount リオーダバッファからレジスタファイルに1クロックでコミットする命令の数(Max)
 * @param debug                      デバッグ機能を使う
 */
class Parameters(val tagWidth: Int = 7,
                 val numberOfDecoders: Int = 2,
                 val numberOfALUs: Int = 2,
                 val maxRegisterFileCommitCount: Int = 4,
                 val debug: Boolean = false)
