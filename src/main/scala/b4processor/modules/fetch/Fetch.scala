package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{Fetch2BranchPrediction, Fetch2Decoder, InstructionCache2Fetch}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class Fetch(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val cache = Flipped(Vec(params.numberOfDecoders, new InstructionCache2Fetch))
    val decoders = Vec(params.numberOfDecoders, new Fetch2Decoder)
    val prediction = Vec(params.numberOfDecoders, new Fetch2BranchPrediction)

    val PC = if (params.debug) Some(Output(SInt(64.W))) else None
    val nextPC = if (params.debug) Some(Output(SInt(64.W))) else None
  })

  val pc = RegInit(params.pcInit.S(64.W))
  val waiting = RegInit(false.B)

  var nextPC = pc
  var nextIsValid = true.B
  var nextWait = waiting
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    val cache = io.cache(i)
    val prediction = io.prediction(i)

    cache.address := nextPC
    decoder.valid := io.cache(i).output.valid
    decoder.bits.programCounter := nextPC
    decoder.bits.instruction := cache.output.bits

    val branch = Module(new CheckBranch)
    branch.io.instruction := io.cache(i).output.bits

    decoder.bits.isBranch := MuxLookup(branch.io.branchType.asUInt, 0.S, Seq(
      BranchType.JAL.asUInt -> branch.io.offset,
      BranchType.Branch.asUInt -> Mux(prediction.prediction, branch.io.offset, 4.S),
      BranchType.None.asUInt -> 4.S,
    ))

    prediction.addressLowerBits := nextPC(params.branchPredictionWidth + 1, 1)
    prediction.isBranch := branch.io.branchType =/= BranchType.None


    nextPC = nextPC + MuxLookup(branch.io.branchType.asUInt, 0.S, Seq(
      BranchType.JAL.asUInt -> branch.io.offset,
      BranchType.Branch.asUInt -> Mux(prediction.prediction, branch.io.offset, 4.S),
      BranchType.None.asUInt -> 4.S,
    ))
    nextWait = nextWait || MuxLookup(branch.io.branchType.asUInt, false.B, Seq(
      BranchType.JALR.asUInt -> true.B,
      BranchType.Fence.asUInt -> true.B,
      BranchType.FenceI.asUInt -> true.B, // TODO: 予測された値を入れる。raの値を使う？
    ))
    nextIsValid = nextIsValid && io.cache(i).output.valid && decoder.ready && !nextWait
  }

  pc := nextPC
  waiting := nextWait

  if (params.debug) {
    io.PC.get := pc
    io.nextPC.get := nextPC
  }
}

object Fetch extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new Fetch(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}