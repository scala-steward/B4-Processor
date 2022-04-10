package b4processor.modules.reorderbuffer

import b4processor.Constants.TAG_WIDTH
import b4processor.connections.{Decoder2ReorderBuffer, ExecutionRegisterBypass, ReorderBuffer2RegisterFile}
import b4processor.modules.reservationstation.ReservationStationEntry
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class ReorderBuffer(numberOfDecoders: Int, numberOfALus: Int, maxRegisterFileCommitCount: Int) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(numberOfDecoders, Flipped(new Decoder2ReorderBuffer))
    val alus = Vec(numberOfALus, Flipped(new ExecutionRegisterBypass))
    val registerFile = Vec(maxRegisterFileCommitCount, new ReorderBuffer2RegisterFile)
  })

  val defaultEntry = {
    val entry = Wire(new ReorderBufferEntry)
    entry.value := 0.U
    entry.ready := false.B
    entry.programCounter := 0.U
    entry.destinationRegister := 0.U
    entry
  }

  val nopEntry = {
    val entry = Wire(new ReorderBufferEntry)
    entry.value := 0.U
    entry.ready := true.B
    entry.programCounter := 0.U
    entry.destinationRegister := 0.U
    entry
  }

  val head = RegInit(0.U(TAG_WIDTH.W))
  val tail = RegInit(0.U(TAG_WIDTH.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, TAG_WIDTH).toInt)(defaultEntry)))

  // デコーダからの読み取りと書き込み
  head := head + numberOfDecoders.U
  for (i <- 0 until numberOfDecoders) {
    val decoder = io.decoders(i)
    buffer(head + i.U) <> Mux(decoder.valid,
      {
        val entry = Wire(new ReorderBufferEntry)
        entry.value := 0.U
        entry.ready := false.B
        entry.programCounter := decoder.bits.programCounter
        entry.destinationRegister := Mux(decoder.bits.destination.destinationRegister.valid, decoder.bits.destination.destinationRegister.bits, 0.U)
        entry
      },
      nopEntry)

    decoder.bits.destination.destinationTag := head + i.U
    // ソースレジスタに対応するタグ、値の代入
    decoder.bits.source1.matchingTag.valid := buffer.map { entry => entry.destinationRegister === decoder.bits.source1.sourceRegister }.fold(false.B) { (a, b) => a | b }
    decoder.bits.source1.matchingTag.bits := MuxCase(0.U, buffer.zipWithIndex.map { case (entry, index) => (entry.destinationRegister === decoder.bits.source1.sourceRegister) -> index.U })
    decoder.bits.source1.value.valid := buffer.map { entry => entry.destinationRegister === decoder.bits.source1.sourceRegister && entry.ready }.fold(false.B) { (a, b) => a | b }
    decoder.bits.source1.value.bits := MuxCase(0.U, buffer.map { entry => (entry.destinationRegister === decoder.bits.source1.sourceRegister && entry.ready) -> entry.value })
    decoder.bits.source2.matchingTag.valid := buffer.map { entry => entry.destinationRegister === decoder.bits.source2.sourceRegister }.fold(false.B) { (a, b) => a | b }
    decoder.bits.source2.matchingTag.bits := MuxCase(0.U, buffer.zipWithIndex.map { case (entry, index) => (entry.destinationRegister === decoder.bits.source2.sourceRegister) -> index.U })
    decoder.bits.source2.value.valid := buffer.map { entry => entry.destinationRegister === decoder.bits.source2.sourceRegister && entry.ready }.fold(false.B) { (a, b) => a | b }
    decoder.bits.source2.value.bits := MuxCase(0.U, buffer.map { entry => (entry.destinationRegister === decoder.bits.source2.sourceRegister && entry.ready) -> entry.value })
  }

  // レジスタファイルへの書き込み
  for (i <- 0 until maxRegisterFileCommitCount) {
    val lastValid = if (i == 0) {
      true.B
    } else {
      io.registerFile(i - 1).valid
    }
    io.registerFile(i).valid := lastValid && tail + i.U <= head && buffer(tail + i.U).ready
    io.registerFile(i).bits := buffer(tail + i.U).value
  }
  tail := tail + MuxCase(0.U, io.registerFile.zipWithIndex.map { case (entry, index) => !entry.valid -> index.U })

  // ALUの読み込み
  for (i <- buffer.indices) {
    val entry = buffer(i)
    entry.ready := entry.ready || io.alus.map { alu => alu.valid }.fold(false.B) { (a, b) => a | b }
    entry.value := MuxCase(entry.value, io.alus.map { alu => (alu.valid && alu.bits.destinationTag === i.U) -> alu.bits.value })
  }
}

object A extends App {
  (new ChiselStage).emitVerilog(new ReorderBuffer(2, 2, 4), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}