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
case class Parameters(tagWidth: Int = 6,
                      numberOfDecoders: Int = 2,
                      numberOfALUs: Int = 2,
                      numberOfReservationStationEntries: Int = 20,
                      maxRegisterFileCommitCount: Int = 4,
                      debug: Boolean = false)
