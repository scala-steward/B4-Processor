package b4processor.modules.memory

import b4processor.utils
import b4processor.Parameters
import b4processor.connections.{LoadStoreQueue2Memory, OutputValue}
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth._
import b4processor.utils.AxiLiteMaster
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class DataMemoryInterface(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn = Flipped(new LoadStoreQueue2Memory)
    val dataOut = new OutputValue
    val master = new AxiLiteMaster(64, 64)
  })

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

  io.dataOut.value := 0.U
  io.dataOut.tag := 0.U
  io.dataOut.validAsResult := false.B
  io.dataOut.validAsLoadStoreAddress := false.B

  io.dataIn.ready := MuxLookup(
    io.dataIn.bits.accessInfo.accessType.asUInt,
    true.B,
    Seq(
      Store.asUInt -> (io.master.writeResp.valid && !io.master.writeResp.bits(1)),
      Load.asUInt -> (io.master.readData.valid && !io.master.readData.bits.resp(1))
    )
  )

  // FIXME AXIのreadyが1クロックで返ってくるか分からないから．その間DataMemoryBufferのvalidを常にtrueにしておく必要がある？
  when(io.dataIn.valid) {
    when(io.dataIn.bits.accessInfo.accessType === Store) {
      io.master.writeAddr.valid := true.B
      io.master.writeData.valid := true.B
      io.master.writeResp.ready := true.B
      when(io.master.writeAddr.ready && io.master.writeData.ready) {
        io.master.writeAddr.bits.addr := io.dataIn.bits.address
        io.master.writeData.bits.data := io.dataIn.bits.data
        io.master.writeData.bits.strb := MuxLookup(
          io.dataIn.bits.accessInfo.accessWidth.asUInt,
          "b00000000".U,
          Seq(
            Byte.asUInt -> "b00000001".U,
            HalfWord.asUInt -> "b00000011".U,
            Word.asUInt -> "b00001111".U,
            DoubleWord.asUInt -> "b11111111".U
          )
        )
      }
    }

    when(io.dataIn.bits.accessInfo.accessType === Load) {
      io.master.readAddr.valid := true.B
      io.master.readData.ready := true.B
      io.master.readAddr.bits.addr := io.dataIn.bits.address
      when(io.master.readAddr.ready && io.master.readData.valid) {
        io.dataOut.tag := io.dataIn.bits.tag
        io.dataOut.validAsResult := true.B
        io.dataOut.value := MuxLookup(
          io.dataIn.bits.accessInfo.accessWidth.asUInt,
          0.U,
          Seq(
            Byte.asUInt -> io.master.readData.bits.data(7, 0),
            HalfWord.asUInt -> io.master.readData.bits.data(15, 0),
            Word.asUInt -> io.master.readData.bits.data(31, 0),
            DoubleWord.asUInt -> io.master.readData.bits.data(63, 0)
          )
        )
      }
    }
  }
}

object DataMemoryInterface extends App {
  implicit val params = {
    Parameters(runParallel = 1, tagWidth = 4)
  }
  (new ChiselStage).emitVerilog(
    new DataMemoryInterface,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
