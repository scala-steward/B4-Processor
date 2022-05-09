package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{Fetch2BranchPrediction, Fetch2Decoder, InstructionCache2Fetch}
import chisel3._
import chisel3.stage.ChiselStage

class Fetch(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val cache = Flipped(Vec(params.numberOfDecoders, new InstructionCache2Fetch))
    val decoders = Vec(params.numberOfDecoders, new Fetch2Decoder)
    val prediction = Vec(params.numberOfDecoders, new Fetch2BranchPrediction)

    val PC = if (params.debug) Some(Output(SInt(64.W))) else None
    val nextPC = if (params.debug) Some(Output(SInt(64.W))) else None
    val isPrediction = if (params.debug) Some(Output(Bool())) else None
    val nextIsPrediction = if (params.debug) Some(Output(Bool())) else None
  })

  val pc = RegInit(params.pcInit.S(64.W))
  val isPrediction = RegInit(false.B)

  var nextPC = pc
  var nextIsPrediction = isPrediction
  var nextIsValid = true.B.suggestName("nextIsValid0")
  for (i <- 0 until params.numberOfDecoders) {
    val decoder = io.decoders(i)
    val cache = io.cache(i)
    val prediction = io.prediction(i)

    cache.address := nextPC
    decoder.valid := io.cache(i).output.valid
    decoder.bits.programCounter := nextPC
    decoder.bits.instruction := cache.output.bits
    decoder.bits.isPrediction := nextIsPrediction

    val branch = Module(new CheckBranch)
    branch.io.instruction := io.cache(i).output.bits

    prediction.addressLowerBits := nextPC(params.branchPredictionWidth + 1, 1)
    prediction.isBranch := branch.io.branchType =/= BranchType.None


    nextPC = nextPC + MuxLookup(branch.io.branchType.asUInt, 4.S, Seq(
      BranchType.JAL -> branch.io.offset,
      BranchType.Branch -> Mux(prediction.prediction, branch.io.offset, 4.S),
      BranchType.JALR -> 0.U, // TODO: 予測された値を入れる。raの値を使う？
    ))
    nextIsPrediction = nextIsPrediction ||
      branch.io.branchType === BranchType.Branch ||
      branch.io.branchType === BranchType.JALR
    nextIsValid = (nextIsValid && io.cache(i).output.valid && decoder.ready)
  }

  pc := nextPC
  isPrediction := nextIsPrediction

  if (params.debug) {
    io.PC.get := pc
    io.nextPC.get := nextPC
    io.isPrediction.get := isPrediction
    io.nextIsPrediction.get := nextIsPrediction
  }
}

object Fetch extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new Fetch(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}