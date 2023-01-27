package b4processor.modules.csr

import b4processor.Parameters
import b4processor.connections.{
  CSR2Fetch,
  CSRReservationStation2CSR,
  OutputValue,
  ReorderBuffer2CSR,
  ResultType
}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class CSR(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoderInput = Flipped(Decoupled(new CSRReservationStation2CSR))
    val CSROutput = Irrevocable(new OutputValue)
    val fetch = Output(new CSR2Fetch)
    val reorderBuffer = Flipped(new ReorderBuffer2CSR)
    val threadId = Input(UInt(log2Up(params.threads).W))
  })

  io.decoderInput.ready := io.CSROutput.ready
  io.CSROutput.valid := false.B
  io.CSROutput.bits.tag := io.decoderInput.bits.destinationTag
  io.CSROutput.bits.value := 0.U
  io.CSROutput.bits.isError := false.B
  io.CSROutput.bits.resultType := ResultType.Result

  io.fetch := DontCare

  val retireCounter = Module(new RetireCounter)
  retireCounter.io.retireInCycle := io.reorderBuffer.retireCount
  val cycleCounter = Module(new CycleCounter)

  val mtvec = RegInit(0.U(64.W))
  io.fetch.mtvec := mtvec
  val mepc = RegInit(0.U(64.W))
  io.fetch.mepc := mepc
  val mcause = RegInit(0.U(64.W))
  io.fetch.mcause := mcause
  val mstatus = RegInit(0.U(64.W))

  when(io.decoderInput.valid) {
    val address = io.decoderInput.bits.address
    io.CSROutput.valid := true.B

    when(address === CSRName.cycle || address === CSRName.mcycle) {
      io.CSROutput.bits.value := cycleCounter.count
    }.elsewhen(address === CSRName.instret || address === CSRName.minstret) {
      io.CSROutput.bits.value := retireCounter.io.count
    }.elsewhen(address === CSRName.mhartid) {
      io.CSROutput.bits.value := io.threadId
    }.elsewhen(address === CSRName.mtvec) {
      io.CSROutput.bits.value := mtvec
      when(io.CSROutput.ready && io.CSROutput.valid) {
        mtvec := MuxLookup(
          io.decoderInput.bits.csrAccessType.asUInt,
          0.U,
          Seq(
            CSRAccessType.ReadWrite.asUInt -> io.decoderInput.bits.value,
            CSRAccessType.ReadSet.asUInt -> (mtvec | io.decoderInput.bits.value),
            CSRAccessType.ReadClear.asUInt -> (mtvec & io.decoderInput.bits.value)
          )
        )
      }
    }.elsewhen(address === CSRName.mepc) {
      io.CSROutput.bits.value := mepc
      when(io.CSROutput.ready && io.CSROutput.valid) {
        mepc := MuxLookup(
          io.decoderInput.bits.csrAccessType.asUInt,
          0.U,
          Seq(
            CSRAccessType.ReadWrite.asUInt -> io.decoderInput.bits.value,
            CSRAccessType.ReadSet.asUInt -> (mepc | io.decoderInput.bits.value),
            CSRAccessType.ReadClear.asUInt -> (mepc & io.decoderInput.bits.value)
          )
        )
      }
    }.elsewhen(address === CSRName.mcause) {
      io.CSROutput.bits.value := mcause
      when(io.CSROutput.ready && io.CSROutput.valid) {
        mcause := MuxLookup(
          io.decoderInput.bits.csrAccessType.asUInt,
          0.U,
          Seq(
            CSRAccessType.ReadWrite.asUInt -> io.decoderInput.bits.value,
            CSRAccessType.ReadSet.asUInt -> (mcause | io.decoderInput.bits.value),
            CSRAccessType.ReadClear.asUInt -> (mcause & io.decoderInput.bits.value)
          )
        )
      }
    }.elsewhen(address === CSRName.mstatus) {
      io.CSROutput.bits.value := mstatus
      when(io.CSROutput.ready && io.CSROutput.valid) {
        mstatus := MuxLookup(
          io.decoderInput.bits.csrAccessType.asUInt,
          0.U,
          Seq(
            CSRAccessType.ReadWrite.asUInt -> io.decoderInput.bits.value,
            CSRAccessType.ReadSet.asUInt -> (mstatus | io.decoderInput.bits.value),
            CSRAccessType.ReadClear.asUInt -> (mstatus & io.decoderInput.bits.value)
          )
        )
      }
    }.otherwise {
      io.CSROutput.bits.isError := true.B
    }
  }
}

object CSR extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitSystemVerilog(new CSR)
}
