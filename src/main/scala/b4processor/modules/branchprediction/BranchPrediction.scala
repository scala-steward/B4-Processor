package b4processor.modules.branchprediction

import b4processor.Parameters
import b4processor.connections.Fetch2BranchPrediction
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

/** 分岐予測モジュール */
class BranchPrediction(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = Flipped(Vec(params.runParallel, new Fetch2BranchPrediction))
  })

  for (f <- io.fetch) {
    f.prediction := true.B
  }
}

object BranchPrediction extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new BranchPrediction())
}
