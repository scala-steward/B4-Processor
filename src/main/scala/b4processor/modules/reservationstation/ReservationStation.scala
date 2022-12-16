package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.connections.{
  CollectedOutput,
  Decoder2ReservationStation,
  ReservationStation2Executor,
  ResultType
}
import b4processor.utils.Tag
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class ReservationStation(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val collectedOutput = Flipped(new CollectedOutput)
    val executor = Irrevocable(new ReservationStation2Executor)
    val decoder = Vec(
      params.threads * params.decoderPerThread,
      Flipped(new Decoder2ReservationStation)
    )
  })

  val rsWidth = log2Up(params.threads * params.decoderPerThread * 2)

  val reservation = RegInit(
    VecInit(
      Seq.fill(math.pow(2, rsWidth).toInt)(ReservationStationEntry.default)
    )
  )
  private val outputArbiter = Module(
    new RRArbiter(new ReservationStation2Executor, math.pow(2, rsWidth).toInt)
  )

  for ((r, i) <- reservation.zipWithIndex) {
    outputArbiter.io.in(i).bits.opcode := r.opcode
    outputArbiter.io.in(i).bits.destinationTag := r.destinationTag
    outputArbiter.io.in(i).bits.value1 := r.value1
    outputArbiter.io.in(i).bits.value2 := r.value2
    outputArbiter.io.in(i).bits.function3 := r.function3
    outputArbiter.io.in(i).bits.immediateOrFunction7 := r.immediateOrFunction7
    outputArbiter.io.in(i).valid := r.ready1 && r.ready2
    when(outputArbiter.io.in(i).valid && outputArbiter.io.in(i).ready) {
      r := ReservationStationEntry.default
    }
  }
  io.executor <> outputArbiter.io.out

  // デコーダから
  private val head = RegInit(0.U(rsWidth.W))
  private var nextHead = head
  for (i <- 0 until (params.decoderPerThread * params.threads)) {
    io.decoder(i).ready := false.B
    when(!reservation(nextHead).valid) {
      io.decoder(i).ready := true.B
      when(io.decoder(i).entry.valid) {
        reservation(nextHead) := io.decoder(i).entry
      }
    }
    nextHead = Mux(
      !reservation(nextHead).valid && io.decoder(i).entry.valid,
      nextHead + 1.U,
      nextHead
    )
  }
  head := nextHead

  private val output = io.collectedOutput.outputs
  when(output.valid && output.bits.resultType === ResultType.Result) {
    for (entry <- reservation) {
      when(entry.valid) {
        when(!entry.ready1 && entry.sourceTag1 === output.bits.tag) {
          entry.value1 := output.bits.value
          entry.ready1 := true.B
        }
        when(!entry.ready2 && entry.sourceTag2 === output.bits.tag) {
          entry.value2 := output.bits.value
          entry.ready2 := true.B
        }
      }
    }
  }

}

object ReservationStation extends App {
  implicit val params =
    Parameters(tagWidth = 2, decoderPerThread = 1, threads = 1)
  (new ChiselStage).emitVerilog(
    new ReservationStation(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
