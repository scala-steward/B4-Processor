package b4processor

object Constants {
  // リオーダバッファで使用するタグのビット数
  val TAG_WIDTH = 7
  // デコーダの数
  val NUMBER_OF_DECODERS = 2
  // ALUの数
  val NUMBER_OF_ALUS = 2
  // リオーダバッファからレジスタファイルに1クロックでコミットする命令の数(Max)
  val MAX_REGISTER_FILE_COMMIT_COUNT = 8
}
