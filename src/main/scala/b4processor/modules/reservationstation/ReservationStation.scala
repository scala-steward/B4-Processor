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
    val executor = new ReservationStation2Executor
    val decoder = Vec(
      params.threads * params.decoderPerThread,
      Flipped(new Decoder2ReservationStation)
    )
  })

  val reservation = RegInit(
    VecInit(
      Seq.fill(math.pow(2, params.reservationStationWidth).toInt)(
        ReservationStationEntry.default
      )
    )
  )
  val readyList = reservation.map { r => r.ready1 && r.ready2 }

  val hasReady = Cat(readyList).orR
  val executeIndex = MuxCase(
    0.U,
    readyList.zipWithIndex.map { case (ready, index) => ready -> index.U }
  )

  //  printf(p"hasEmpty=$hasEmpty at $emptyIndex hasReady=$hasReady at $executeIndex\n")
  //  printf(p"reserved0 valid=${reservation(0).valid} ready1=${reservation(0).ready1} value1=${reservation(0).value1}\n")

  // 実行ユニットへ
  when(io.executor.ready && hasReady) {
    reservation(executeIndex) := 0.U.asTypeOf(new ReservationStationEntry)
    //      printf(p"from reserved $executeIndex\n")
  }.otherwise {
    //      printf("no output\n")
  }
  io.executor.valid := hasReady
  when(io.executor.valid) {
    io.executor.bits.opcode := reservation(executeIndex).opcode
    io.executor.bits.destinationTag := reservation(executeIndex).destinationTag
    io.executor.bits.value1 := reservation(executeIndex).value1
    io.executor.bits.value2 := reservation(executeIndex).value2
    io.executor.bits.programCounter := reservation(executeIndex).programCounter
    io.executor.bits.function3 := reservation(executeIndex).function3
    io.executor.bits.immediateOrFunction7 := reservation(
      executeIndex
    ).immediateOrFunction7
  }.otherwise {
    io.executor.bits.opcode := 0.U
    io.executor.bits.destinationTag := Tag(0, 0)
    io.executor.bits.value1 := 0.U
    io.executor.bits.value2 := 0.U
    io.executor.bits.programCounter := 0.U
    io.executor.bits.function3 := 0.U
    io.executor.bits.immediateOrFunction7 := 0.U
  }

  // デコーダから
  private val head = RegInit(0.U(params.reservationStationWidth.W))
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
