package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{Fetch2BranchPrediction, Fetch2Decoder, InstructionCache2Fetch}
import chisel3._

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
  var nextIsValid = true.B
  for (i <- 0 until params.numberOfDecoders) {
    io.cache(i).address := nextPC
    io.decoders(i).valid := io.cache(i).output.valid
    io.decoders(i).bits.programCounter := nextPC
    io.decoders(i).bits.instruction := io.cache(i).output.bits
    io.decoders(i).bits.isPrediction := nextIsPrediction

    val branch = Module(new CheckBranch)
    branch.input.instruction := io.cache(i).output.bits
    branch.input.programCounter := nextPC

    io.prediction(i).addressLowerBits := nextPC(params.branchPredictionWidth + 1, 1)
    io.prediction(i).isBranch := branch.output.isBranch


    nextPC = Mux(branch.output.isBranch && io.prediction(i).prediction, branch.output.branchAddress, nextPC + 4.S)
    nextIsPrediction = nextIsPrediction || branch.output.isBranch
    nextIsValid = nextIsValid && io.cache(i).output.valid
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
