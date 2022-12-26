package b4processor.modules.csr

import b4processor.Parameters
import b4processor.connections.{CSR2Fetch, OutputValue, ResultType}
import b4processor.utils.Tag
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class CSR(hartid: Int)(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoderInput = Flipped(Valid(new Bundle {
      val address = UInt(12.W)
      val value = UInt(64.W)
      val destinationTag = new Tag
    }))
    val CSROutput = Valid(new OutputValue)
    val collectedOutput = Flipped(Valid(new OutputValue))
    val fetch = Valid(new CSR2Fetch)
    val reorderBuffer = Flipped(new ReorderBuffer2CSR)
  })

  io.CSROutput.valid := false.B
  io.CSROutput.bits.tag := io.decoderInput.bits.destinationTag
  io.CSROutput.bits.value := 0.U
  io.CSROutput.bits.isError := false.B
  io.CSROutput.bits.resultType := ResultType.Result

  io.fetch := DontCare

  val retireCounter = Module(new RetireCounter)
  retireCounter.io.retireInCycle := io.reorderBuffer.retireCount
  val cycleCounter = Module(new CycleCounter)

  when(io.decoderInput.valid) {
    val address = io.decoderInput.bits.address
    io.CSROutput.valid := true.B

    when(address === CSRName.cycle) {
      io.CSROutput.bits.value := cycleCounter.count
    }.elsewhen(address === CSRName.instret) {
      io.CSROutput.bits.value := retireCounter.io.count
    }.elsewhen(address === CSRName.mhartid) {
      io.CSROutput.bits.value := hartid.U
    }.otherwise {
      io.CSROutput.bits.isError := true.B
    }
  }
}

object CSR extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new CSR(0))
}
