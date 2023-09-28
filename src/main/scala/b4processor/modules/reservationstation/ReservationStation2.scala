package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.connections.{
  CollectedOutput,
  Decoder2ReservationStation,
  ReservationStation2Executor,
  ReservationStation2PExtExecutor,
}
import b4processor.utils.MMArbiter
import chisel3._
import chisel3.experimental.prefix
import circt.stage.ChiselStage
import chisel3.util._

class ReservationStation2(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val collectedOutput = Flipped(new CollectedOutput)
    val issue = Irrevocable(new ReservationStation2Executor)
    val pextIssue = Irrevocable(new ReservationStation2PExtExecutor())
    val decoder = Flipped(new Decoder2ReservationStation)
  })

  val rsWidth = 3

  val reservation = RegInit(
    VecInit(
      Seq.fill(math.pow(2, rsWidth).toInt)(ReservationStationEntry.default),
    ),
  )

  private val outputArbiter = Module(
    new Arbiter(new ReservationStation2Executor, reservation.length),
  )

  private val pextOutputArbiter = Module(
    new Arbiter(new ReservationStation2PExtExecutor(), reservation.length),
  )

  for (i <- 0 until reservation.length) {
    prefix(s"issue${i % params.decoderPerThread}_resv$i") {
      val a = outputArbiter.io.in(i)
      val r = reservation(i)
      a.bits.operation := r.operation
      a.bits.destinationTag := r.destinationTag
      a.bits.value1 := r.sources(0).getValueUnsafe
      a.bits.value2 := r.sources(1).getValueUnsafe
      a.bits.wasCompressed := r.wasCompressed
      a.bits.branchOffset := r.branchOffset
      a.valid := r.valid &&
        r.sources(0).isValue &&
        r.sources(1).isValue &&
        !r.ispext
      when(a.valid && a.ready) {
        r := ReservationStationEntry.default
      }
    }

  }
  io.issue <> outputArbiter.io.out

  for (i <- 0 until reservation.length) {
    prefix(s"issue${i % params.decoderPerThread}_resv$i") {
      val a = pextOutputArbiter.io.in(i)
      val r = reservation(i)
      a.bits.operation := r.pextOperation
      a.bits.destinationTag := r.destinationTag
      a.bits.value1 := r.sources(0).getValueUnsafe
      a.bits.value2 := r.sources(1).getValueUnsafe
      a.bits.value3 := r.sources(2).getValueUnsafe
      a.valid := r.valid &&
        r.sources(0).isValue &&
        r.sources(1).isValue &&
        r.sources(2).isValue &&
        r.ispext
      when(a.valid && a.ready) {
        r := ReservationStationEntry.default
      }
    }

  }
  io.pextIssue <> pextOutputArbiter.io.out

  // デコーダから
  private val head = RegInit(0.U(rsWidth.W))

  val decoder = io.decoder
  val resNext = reservation(head)
  decoder.ready := false.B
  when(!resNext.valid) {
    decoder.ready := true.B
    when(decoder.entry.valid) {
      resNext := decoder.entry
      head := head + 1.U
    }
  }

  for ((o, i) <- io.collectedOutput.outputs.zipWithIndex) {
    prefix(s"out$i") {
      when(o.valid) {
        for (entry <- reservation) {
          when(entry.valid) {
            for (source <- entry.sources) {
              source := source.matchExhaustive(
                { tag =>
                  Mux(
                    tag === o.bits.tag,
                    source.fromValue(o.bits.value),
                    source.fromTag(tag),
                  )
                },
                { v => source.fromValue(v) },
              )
            }
          }
        }
      }

    }
  }
}

object ReservationStation2 extends App {
  implicit val params =
    Parameters(tagWidth = 3, decoderPerThread = 2, threads = 2)
  ChiselStage.emitSystemVerilogFile(
    new ReservationStation2(),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,noAlwaysComb",
    ),
  )
}
