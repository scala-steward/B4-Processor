package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.InstructionMemoryInterface2Cache
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth._
import b4processor.utils.AxiLiteMaster
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class InstructionMemoryInterface(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = new InstructionMemoryInterface2Cache
    val master = new AxiLiteMaster(64, 32*params.fetchWidth)
  })

  io.fetch.output.valid := false.B
  io.fetch.output.bits := 0.U

  io.master.readAddr.valid := false.B
  io.master.readAddr.bits.addr := 0.S
  io.master.readAddr.bits.prot := 0.U
  io.master.readData.ready := false.B

  io.master.writeAddr.valid := false.B
  io.master.writeAddr.bits.addr := 0.S
  io.master.writeAddr.bits.prot := 0.U
  io.master.writeData.valid := false.B
  io.master.writeData.bits.data := 0.U
  io.master.writeData.bits.strb := "b11111111".U
  io.master.writeResp.ready := false.B

  when(io.master.readAddr.valid && io.master.readAddr.ready) {
    io.master.readData.ready := true.B
    io.master.readAddr.bits.addr := io.fetch.address
    when(io.master.readData.valid && io.master.readData.ready) {
        io.fetch.output.bits := io.master.readData.bits.data
      io.fetch.output.valid := true.B
    }
  }
}

object InstructionMemoryInterface extends App {
  implicit val params = {
    Parameters(runParallel = 1, tagWidth = 4)
  }
  (new ChiselStage).emitVerilog(
      new InstructionMemoryInterface,
      args = Array(
        "--emission-options=disableMemRandomization,disableRegisterRandomization"
      )
    )
}
