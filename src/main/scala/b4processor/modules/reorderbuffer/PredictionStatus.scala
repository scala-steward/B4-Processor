package b4processor.modules.reorderbuffer

import chisel3.experimental.ChiselEnum

object PredictionStatus extends ChiselEnum {

  /** 分岐ではない */
  val NotBranch = Value

  /** 予測である */
  val Predicted = Value

  /** 予測結果が正しい */
  val Correct = Value

  /** 予測結果が間違っている */
  val Incorrect = Value
}
