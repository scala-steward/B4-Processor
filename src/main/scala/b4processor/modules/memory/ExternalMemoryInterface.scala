package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.OutputValue
import b4processor.structures.memoryAccess.MemoryAccessWidth
import b4processor.utils.{B4RRArbiter, FIFO, Tag}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import b4processor.utils.axi.{BurstSize, BurstType, ChiselAXI, Lock, Response}

class ExternalMemoryInterface(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataWriteRequests = Flipped(Irrevocable(new MemoryWriteTransaction))
    val dataReadRequests = Flipped(Irrevocable(new MemoryReadTransaction))
    val amoWriteRequests = Flipped(Irrevocable(new MemoryWriteTransaction))
    val amoReadRequests = Flipped(Irrevocable(new MemoryReadTransaction))
    val instructionFetchRequest =
      Vec(params.threads, Flipped(Irrevocable(new MemoryReadTransaction)))
    val dataReadOut = Irrevocable(new OutputValue)
    val dataWriteOut = Irrevocable(new OutputValue)
    val amoReadOut = Irrevocable(new OutputValue)
    val amoWriteOut = Irrevocable(new OutputValue)
    val instructionOut = Vec(params.threads, Valid(new InstructionResponse))
    val coordinator = new ChiselAXI(64, 64)
  })

  // setDefaultOutputs
  locally {
    import io.coordinator._
    writeAddress.bits.BURST := BurstType.Incr
    writeResponse.ready := false.B
    write.valid := false.B
    readAddress.bits.USER := 0.U
    writeAddress.bits.USER := 0.U
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
    writeAddress.bits.SIZE := BurstSize.Size1
    write.bits.STRB := 0.U
    readAddress.bits.PROT := 0.U
    readAddress.bits.ADDR := 0.U
    readAddress.bits.LOCK := Lock.Normal
    readAddress.bits.SIZE := BurstSize.Size1
    read.ready := false.B
    write.bits.LAST := false.B
    readAddress.bits.ID := 0.U
    writeAddress.bits.QOS := 0.U
    writeAddress.bits.LEN := 0.U
    writeAddress.valid := false.B
    write.bits.DATA := 0.U
    readAddress.bits.BURST := BurstType.Incr
  }
  io.dataWriteRequests.ready := false.B
  io.dataReadOut.bits.value := 0.U
  io.dataReadOut.bits.isError := false.B
  io.dataReadOut.bits.tag := Tag(0, 0)
  io.dataReadRequests.ready := false.B
  io.dataWriteOut.valid := false.B
  io.dataWriteOut.bits := DontCare
  io.dataReadOut.valid := false.B
  io.amoReadOut.valid := false.B
  io.amoReadOut.bits := 0.U.asTypeOf(new OutputValue)
  io.amoWriteOut.valid := false.B
  io.amoWriteOut.bits := 0.U.asTypeOf(new OutputValue)
  for (tid <- 0 until params.threads) {
    io.instructionFetchRequest(tid).ready := false.B
    io.instructionOut(tid).valid := false.B
    io.instructionOut(tid).bits.inner := 0.U
  }

  // READ OPERATION -------------------------------------------
  val readQueue = Module(new FIFO(3)(new Bundle {
    val burstLength = UInt(8.W)
    val accessType = MemoryReadIntent.Type()
    val tag = new Tag
    val size = new MemoryAccessWidth.Type()
    val offset = UInt(3.W)
    val signed = Bool()
  }))
  readQueue.output.ready := false.B
  readQueue.input.valid := false.B
  readQueue.input.bits := DontCare

  private val instructionsArbiter = Module(
    new B4RRArbiter(new MemoryReadTransaction(), params.threads)
  )
  for (tid <- 0 until params.threads)
    instructionsArbiter.io.in(tid) <> io.instructionFetchRequest(tid)

  private val instructionOrReadDataArbiter = Module(
    new Arbiter(new MemoryReadTransaction(), 3)
  )
  instructionOrReadDataArbiter.io.in(0) <> instructionsArbiter.io.out
  instructionOrReadDataArbiter.io.in(1) <> io.dataReadRequests
  instructionOrReadDataArbiter.io.in(2) <> io.amoReadRequests
  private val instructionOrReadDataQueue = Module(
    new FIFO(2)(new MemoryReadTransaction())
  )
  instructionOrReadDataQueue.input <> instructionOrReadDataArbiter.io.out
//  instructionOrReadDataQueue.flush := false.B
  private val readTransaction = instructionOrReadDataQueue.output
  readTransaction.ready := false.B

  val readQueued = RegInit(false.B)
  val burstLen = RegInit(0.U(8.W))
  when(!readQueue.full) {
    when(readTransaction.valid) {
      locally {
        import io.coordinator.readAddress._
        valid := true.B
        bits.ADDR := readTransaction.bits.address(63, 3) ## 0.U(3.W)
        bits.LEN := readTransaction.bits.burstLength
        bits.SIZE := BurstSize.Size8
        bits.CACHE := "b0010".U
      }
      readQueue.input.valid := !readQueued
      readQueue.input.bits.burstLength := readTransaction.bits.burstLength
      readQueue.input.bits.accessType := readTransaction.bits.accessType
      readQueue.input.bits.tag := readTransaction.bits.outputTag
      readQueue.input.bits.offset := readTransaction.bits.address(2, 0)
      readQueue.input.bits.size := readTransaction.bits.size
      readQueue.input.bits.signed := readTransaction.bits.signed
      readQueued := true.B
      when(io.coordinator.readAddress.ready) {
        readTransaction.ready := true.B
        readQueued := false.B
      }
    }

  }

  when(!readQueue.empty) {
    when(readQueue.output.bits.accessType === MemoryReadIntent.Instruction) {
      io.coordinator.read.ready := true.B
    }.elsewhen(readQueue.output.bits.accessType === MemoryReadIntent.Data) {
      io.coordinator.read.ready := io.dataReadOut.ready
    }.elsewhen(readQueue.output.bits.accessType === MemoryReadIntent.Amo) {
      io.coordinator.read.ready := io.amoReadOut.ready
    }
    when(io.coordinator.read.valid) {
      when(io.coordinator.read.ready) {
        burstLen := burstLen + 1.U
        when(burstLen === readQueue.output.bits.burstLength) {
          readQueue.output.ready := true.B
          burstLen := 0.U
        }
      }
      when(readQueue.output.bits.accessType === MemoryReadIntent.Instruction) {
        val tid = readQueue.output.bits.tag.threadId
        io.instructionOut(tid).valid := true.B
        val data = io.coordinator.read.bits.DATA
        io.instructionOut(tid).bits.inner := data
      }.elsewhen(readQueue.output.bits.accessType === MemoryReadIntent.Data) {
        io.dataReadOut.valid := true.B
        io.dataReadOut.bits.tag := readQueue.output.bits.tag
        val data = io.coordinator.read.bits.DATA
        val isError = io.coordinator.read.bits.RESP =/= Response.Okay
        io.dataReadOut.bits.value := Mux(
          isError,
          5.U,
          Mux1H(
            Seq(
              (readQueue.output.bits.size === MemoryAccessWidth.Byte) -> Mux1H(
                (0 until 8).map(i =>
                  (readQueue.output.bits.offset === i.U) -> Cat(
                    Mux(
                      readQueue.output.bits.signed && data(i * 8 + 7),
                      "xFFFF_FFFF_FFFF_FF".U,
                      0.U
                    ),
                    data(i * 8 + 7, i * 8)
                  )
                )
              ),
              (readQueue.output.bits.size === MemoryAccessWidth.HalfWord) -> Mux1H(
                Seq(0, 2, 4, 6).map(i =>
                  (readQueue.output.bits.offset === i.U) -> Cat(
                    Mux(
                      readQueue.output.bits.signed && data(i * 8 + 15),
                      "xFFFF_FFFF_FFFF".U,
                      0.U
                    ),
                    data(i * 8 + 15, i * 8)
                  )
                )
              ),
              (readQueue.output.bits.size === MemoryAccessWidth.Word) -> Mux1H(
                Seq(0, 4).map(i =>
                  (readQueue.output.bits.offset === i.U) -> Cat(
                    Mux(
                      readQueue.output.bits.signed && data(i * 8 + 31),
                      "xFFFF_FFFF".U,
                      0.U
                    ),
                    data(i * 8 + 31, i * 8)
                  )
                )
              ),
              (readQueue.output.bits.size === MemoryAccessWidth.DoubleWord) -> data
            )
          )
        )
        io.dataReadOut.bits.isError := isError
      }.elsewhen(readQueue.output.bits.accessType === MemoryReadIntent.Amo) {
        io.amoReadOut.valid := true.B
        io.amoReadOut.bits.tag := readQueue.output.bits.tag
        val data = io.coordinator.read.bits.DATA
        val isError = io.coordinator.read.bits.RESP =/= Response.Okay
        io.amoReadOut.bits.value := Mux(
          isError,
          7.U,
          MuxCase(
            0.U,
            Seq(
              (readQueue.output.bits.size === MemoryAccessWidth.Word) -> Mux1H(
                Seq(0, 4).map(i =>
                  (readQueue.output.bits.offset === i.U) -> Cat(
                    Mux(
                      readQueue.output.bits.signed && data(i * 8 + 31),
                      "xFFFF_FFFF".U,
                      0.U
                    ),
                    data(i * 8 + 31, i * 8)
                  )
                )
              ),
              (readQueue.output.bits.size === MemoryAccessWidth.DoubleWord) -> data
            )
          )
        )
        io.amoReadOut.bits.isError := isError
      }
    }
  }

  // ----------------------------------------
  // WRITE OPERATION ------------------------
  // ----------------------------------------
  val dataWriteArbiter = Module(new Arbiter(new MemoryWriteTransaction, 2))
  dataWriteArbiter.io.in(0) <> io.amoWriteRequests
  dataWriteArbiter.io.in(1) <> io.dataWriteRequests

  val dataWriteRequestQueue = Module(new FIFO(3)(new MemoryWriteTransaction))
  dataWriteRequestQueue.input <> dataWriteArbiter.io.out
  dataWriteRequestQueue.output.ready := false.B

  val dataWriteQueue = Module(new FIFO(3)(new Bundle {
    val data = UInt(64.W)
    val strb = UInt(8.W)
  }))
  dataWriteQueue.output.ready := false.B
  dataWriteQueue.input.valid := false.B
  dataWriteQueue.input.bits := DontCare
//  dataWriteQueue.flush := false.B
  val writeResponseQueue = Module(new FIFO(3)(new Bundle {
    val tag = new Tag()
    val accessType = new MemoryWriteIntent.Type()
  }))
  writeResponseQueue.output.ready := false.B
  writeResponseQueue.input.valid := false.B
  writeResponseQueue.input.bits := DontCare
//  writeResponseQueue.flush := false.B

  val writeQueued = RegInit(false.B)
  when(dataWriteRequestQueue.output.valid) {
    locally {
      import io.coordinator.writeAddress._
      valid := true.B
      bits.ADDR := dataWriteRequestQueue.output.bits.address
      bits.LEN := 0.U
      bits.SIZE := BurstSize.Size8
      bits.BURST := BurstType.Incr
      bits.CACHE := "b0010".U

      dataWriteQueue.input.valid := !writeQueued
      dataWriteQueue.input.bits.data := dataWriteRequestQueue.output.bits.data
      dataWriteQueue.input.bits.strb := dataWriteRequestQueue.output.bits.mask
      writeResponseQueue.input.valid := !writeQueued
      writeResponseQueue.input.bits.tag := dataWriteRequestQueue.output.bits.outputTag
      writeResponseQueue.input.bits.accessType := dataWriteRequestQueue.output.bits.accessType
      writeQueued := true.B
    }
    when(io.coordinator.writeAddress.ready) {
      dataWriteRequestQueue.output.ready := true.B
      writeQueued := false.B
    }
  }

  when(!dataWriteQueue.empty) {
    io.coordinator.write.valid := true.B
    io.coordinator.write.bits.DATA := dataWriteQueue.output.bits.data
    io.coordinator.write.bits.STRB := dataWriteQueue.output.bits.strb
    io.coordinator.write.bits.LAST := true.B
    when(io.coordinator.write.ready) {
      dataWriteQueue.output.ready := true.B
    }
  }

  when(!writeResponseQueue.empty) {
    when(writeResponseQueue.output.bits.accessType === MemoryWriteIntent.Data) {
      io.coordinator.writeResponse.ready := io.dataWriteOut.ready
      when(io.coordinator.writeResponse.valid) {
        io.dataWriteOut.valid := true.B
        io.dataWriteOut.bits.tag := writeResponseQueue.output.bits.tag
        io.dataWriteOut.bits.isError := io.coordinator.writeResponse.bits.RESP =/= Response.Okay
        io.dataWriteOut.bits.value := 7.U
        when(io.dataWriteOut.ready) {
          writeResponseQueue.output.ready := true.B
        }
      }
    }.elsewhen(
      writeResponseQueue.output.bits.accessType === MemoryWriteIntent.Amo
    ) {
      io.coordinator.writeResponse.ready := io.amoWriteOut.ready
      when(io.coordinator.writeResponse.valid) {
        io.amoWriteOut.valid := true.B
        io.amoWriteOut.bits.tag := writeResponseQueue.output.bits.tag
        io.amoWriteOut.bits.isError := io.coordinator.writeResponse.bits.RESP =/= Response.Okay
        io.amoWriteOut.bits.value := 7.U
        when(io.amoWriteOut.ready) {
          writeResponseQueue.output.ready := true.B
        }
      }
    }.otherwise { assert(false.B) }
  }

}

object ExternalMemoryInterface extends App {
  implicit val params = {
    Parameters(decoderPerThread = 1, tagWidth = 4)
  }
  ChiselStage.emitSystemVerilogFile(new ExternalMemoryInterface)
}
