package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.connections.{ExecutionRegisterBypass, ReservationStation2Executor}
import chisel3._
import chisel3.util._

class ReservationStation(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val alu = Flipped(Vec(params.numberOfALUs, new ExecutionRegisterBypass))
    val executor = new ReservationStation2Executor
    val decoder = Flipped(DecoupledIO(new ReservationStationEntry))
  })

  val defaultEntry = 0.U.asTypeOf(new ReservationStationEntry)
  // エントリを2^タグ幅/デコーダの数で初期化
  val reservation = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt / params.numberOfDecoders)(defaultEntry)))
  val readyList = reservation.map { r => r.ready1 && r.ready2 }

  val head = RegInit(0.U(params.tagWidth))
  val tail = RegInit(0.U(params.tagWidth))

  io.decoder.ready := head + 1.U =/= tail
  when(io.decoder.valid) {
    reservation(head) := io.decoder.bits
    head := head + 1.U
  }

  when(io.executor.ready) {

  }
}
