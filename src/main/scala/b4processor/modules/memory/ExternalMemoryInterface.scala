package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.OutputValue
import b4processor.utils.{AXI, BurstSize, BurstType, FIFO, Lock, Response, Tag}
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

class ExternalMemoryInterface(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataWriteRequests = Flipped(Irrevocable(new MemoryWriteTransaction))
    val dataReadRequests = Flipped(Irrevocable(new MemoryReadTransaction))
    val instructionFetchRequest =
      Flipped(Irrevocable(new InstructionFetchTransaction))
    val dataReadOut = Valid(new OutputValue)
    val dataWriteOut = Valid(new WriteResponse)
    val instructionOut = Irrevocable(new InstructionResponse)
    val coordinator = new AXI(64)
  })

  // setDefaultOutputs
  locally {
    import io.coordinator._
    writeAddress.bits.BURST := BurstType.Incr
    writeResponse.ready := false.B
    write.valid := false.B
    readAddress.bits.USER := 0.U
    readAddress.bits.REGION := 0.U
    writeAddress.bits.USER := 0.U
    writeAddress.bits.REGION := 0.U
    writeAddress.bits.PROT := 0.U
    readAddress.bits.CACHE := 0.U
    writeAddress.bits.ADDR := 0.U
    writeAddress.bits.LOCK := Lock.Normal
    write.bits.USER := 0.U
    writeAddress.bits.ID := 0.U
    readAddress.valid := false.B
    writeAddress.bits.CACHE := 0.U
    readAddress.bits.LEN := 0.U
    readAddress.bits.QOS := 0.U
    writeAddress.bits.SIZE := BurstSize.Size64
    write.bits.STRB := 0.U
    readAddress.bits.PROT := 0.U
    readAddress.bits.ADDR := 0.U
    readAddress.bits.LOCK := Lock.Normal
    readAddress.bits.SIZE := BurstSize.Size64
    read.ready := false.B
    write.bits.LAST := false.B
    readAddress.bits.ID := 0.U
    writeAddress.bits.QOS := 0.U
    writeAddress.bits.LEN := 0.U
    writeAddress.valid := true.B
    write.bits.ID := 0.U
    write.bits.DATA := 0.U
    readAddress.bits.BURST := BurstType.Incr
  }
  io.dataWriteRequests.ready := false.B
  io.dataReadOut.valid := false.B
  io.dataReadOut.bits.validAsResult := false.B
  io.dataReadOut.bits.validAsLoadStoreAddress := false.B
  io.dataReadOut.bits.value := 0.U
  io.dataReadOut.bits.tag := Tag(0)
  io.instructionFetchRequest.ready := false.B
  io.dataReadRequests.ready := false.B
  io.dataWriteOut.valid := false.B
  io.instructionOut.valid := false.B
  io.instructionOut.bits.inner := 0.U
  io.dataWriteOut.bits := DontCare

  // READ OPERATION -------------------------------------------
  class ReadState extends Bundle {
    val burstLength = UInt(8.W)
    val isInstruction = Bool()
    val tag = new Tag
  }
  val readQueue = Module(new FIFO[ReadState](8)(new ReadState))
  readQueue.output.ready := false.B
  readQueue.input.valid := true.B
  readQueue.input.bits := DontCare

  when(!readQueue.full) {
    when(io.instructionFetchRequest.valid) {
      locally {
        import io.coordinator.readAddress._
        valid := true.B
        bits.ADDR := io.instructionFetchRequest.bits.address
        bits.LEN := io.instructionFetchRequest.bits.burstLength
        bits.SIZE := BurstSize.Size64
      }
      when(io.coordinator.readAddress.ready) {
        io.instructionFetchRequest.ready := true.B
        readQueue.input.valid := true.B
        readQueue.input.bits.burstLength := io.instructionFetchRequest.bits.burstLength
        readQueue.input.bits.isInstruction := true.B
        readQueue.input.bits.tag := Tag(0)
      }
    }.elsewhen(io.dataReadRequests.valid) {
      locally {
        import io.coordinator.readAddress._
        valid := true.B
        bits.ADDR := io.dataReadRequests.bits.address
        bits.LEN := 0.U
        bits.SIZE := BurstSize.Size64
      }
      when(io.coordinator.readAddress.ready) {
        io.dataReadRequests.ready := true.B
        readQueue.input.valid := true.B
        readQueue.input.bits.burstLength := 0.U
        readQueue.input.bits.isInstruction := false.B
        readQueue.input.bits.tag := io.dataReadRequests.bits.outputTag
      }
    }
    val burstLen = Reg(UInt(8.W))
    when(!readQueue.empty) {
      io.coordinator.read.ready := true.B
      when(io.coordinator.read.valid) {
        burstLen := burstLen + 1.U
        io.dataReadOut.valid := true.B
        when(readQueue.output.bits.isInstruction) {
          io.instructionOut.valid := true.B
          io.instructionOut.bits.inner := io.coordinator.read.bits.DATA
        }.otherwise {
          io.dataReadOut.bits.tag := readQueue.output.bits.tag
          io.dataReadOut.bits.value := io.coordinator.read.bits.DATA
          io.dataReadOut.bits.validAsResult := true.B
          io.dataReadOut.bits.validAsLoadStoreAddress := false.B
          io.dataReadOut.bits.isError := io.coordinator.read.bits.RESP =/= Response.Okay
        }
      }
      when(burstLen === readQueue.output.bits.burstLength) {
        readQueue.output.ready
      }
    }
    // ----------------------------------------
    // WRITE OPERATION ------------------------
    // ----------------------------------------
    class DataWriteState extends Bundle {
      val data = UInt(64.W)
      val tag = new Tag
      val strb = UInt(8.W)
    }
    val dataWriteQueue = Module(new FIFO(4)(new DataWriteState))
    dataWriteQueue.output.ready := false.B
    dataWriteQueue.input.valid := false.B
    dataWriteQueue.input.bits := DontCare
    class WriteResponseState extends Bundle {
      val tag = new Tag
    }
    val writeResponseQueue = Module(new FIFO(4)(new WriteResponseState))
    writeResponseQueue.output.ready := false.B
    writeResponseQueue.input.valid := false.B
    writeResponseQueue.input.bits := DontCare

    when(io.dataWriteRequests.valid) {
      locally {
        import io.coordinator.writeAddress._
        valid := true.B
        bits.ADDR := io.dataWriteRequests.bits.address
        bits.LEN := 0.U
        bits.SIZE := BurstSize.Size64
        bits.BURST := BurstType.Incr
      }
      when(io.coordinator.writeAddress.ready) {
        io.dataWriteRequests.ready := true.B
        dataWriteQueue.input.valid := true.B
        dataWriteQueue.input.bits.data := io.dataWriteRequests.bits.data
        dataWriteQueue.input.bits.tag := io.dataWriteRequests.bits.outputTag
        dataWriteQueue.input.bits.strb := io.dataWriteRequests.bits.mask
      }
    }

    when(!dataWriteQueue.empty) {
      io.coordinator.write.valid := true.B
      io.coordinator.write.bits.DATA := dataWriteQueue.output.bits.data
      io.coordinator.write.bits.STRB := dataWriteQueue.output.bits.strb
      io.coordinator.write.bits.LAST := true.B
      when(io.coordinator.write.ready) {
        dataWriteQueue.output.ready := true.B
        writeResponseQueue.input.valid := true.B
        writeResponseQueue.input.bits.tag := dataWriteQueue.output.bits.tag
      }
    }

    when(!writeResponseQueue.empty) {
      io.coordinator.writeResponse.ready := true.B
      when(io.coordinator.writeResponse.valid) {
        writeResponseQueue.output.ready := true.B
        io.dataWriteOut.valid := true.B
        io.dataWriteOut.bits.tag := writeResponseQueue.output.bits.tag
        io.dataWriteOut.bits.isError := false.B
      }
    }
  }
}

object ExternalMemoryInterface extends App {
  implicit val params = {
    Parameters(runParallel = 1, tagWidth = 4)
  }
  (new ChiselStage).emitVerilog(
    new ExternalMemoryInterface,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
