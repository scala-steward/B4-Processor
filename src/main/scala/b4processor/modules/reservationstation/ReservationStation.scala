package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.connections.{
  CollectedOutput,
  Decoder2ReservationStation,
  ReservationStation2Executor,
  ResultType
}
import b4processor.utils.B4RRArbiter
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

  val rsWidth = log2Up(params.threads * params.decoderPerThread * 4)

  val reservation = RegInit(
    VecInit(
      Seq.fill(math.pow(2, rsWidth).toInt)(ReservationStationEntry.default)
    )
  )
  private val outputArbiter = Module(
    new B4RRArbiter(new ReservationStation2Executor, math.pow(2, rsWidth).toInt)
  )

  for ((r, a) <- reservation.zip(outputArbiter.io.in)) {
    a.bits.opcode := r.opcode
    a.bits.destinationTag := r.destinationTag
    a.bits.value1 := r.value1
    a.bits.value2 := r.value2
    a.bits.function3 := r.function3
    a.bits.immediateOrFunction7 := r.immediateOrFunction7
    a.valid := r.ready1 && r.ready2
    when(a.valid && a.ready) {
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
