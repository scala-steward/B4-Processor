package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.LoadStoreQueue2Memory
import b4processor.modules.memory.{
  MemoryReadTransaction,
  MemoryWriteTransaction
}
import b4processor.structures.memoryAccess.{MemoryAccessType, MemoryAccessWidth}
import b4processor.utils.FIFO
import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage

/** from LSQ toDataMemory のためのバッファ
  *
  * @param params
  *   パラメータ
  */
class DataMemoryBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn =
      Vec(params.threads, Flipped(Irrevocable(new LoadStoreQueue2Memory)))
    val dataReadRequest = Irrevocable(new MemoryReadTransaction())
    val dataWriteRequest = Irrevocable(new MemoryWriteTransaction())
  })

  private val inputArbiter = Module(
    new RRArbiter(new DataMemoryBufferEntry, params.threads)
  )
  for (tid <- 0 until params.threads)
    inputArbiter.io.in(tid) <> io.dataIn(tid)

  private val buffer = Module(
    new FIFO(params.loadStoreQueueIndexWidth)(new DataMemoryBufferEntry)
  )
  buffer.input <> inputArbiter.io.out
  buffer.output.ready := false.B

  io.dataReadRequest.bits := DontCare
  io.dataReadRequest.valid := false.B
  io.dataWriteRequest.bits := DontCare
  io.dataWriteRequest.valid := false.B

  when(!buffer.empty) {
    val entry = buffer.output.bits
    when(entry.accessInfo.accessType === MemoryAccessType.Load) {
      io.dataReadRequest.valid := true.B
      io.dataReadRequest.bits.address := entry.address
      io.dataReadRequest.bits.size := entry.accessInfo.accessWidth
      io.dataReadRequest.bits.outputTag := entry.tag
      io.dataReadRequest.bits.signed := entry.accessInfo.signed
      when(io.dataReadRequest.ready) {
        buffer.output.ready := true.B
      }
    }.elsewhen(entry.accessInfo.accessType === MemoryAccessType.Store) {
      io.dataWriteRequest.valid := true.B
      val addressUpper = entry.address(63, 3)
      val addressLower = entry.address(2, 0)
      io.dataWriteRequest.bits.address := addressUpper ## 0.U(3.W)
      io.dataWriteRequest.bits.outputTag := entry.tag
      io.dataWriteRequest.bits.data := MuxLookup(
        entry.accessInfo.accessWidth.asUInt,
        0.U,
        Seq(
          MemoryAccessWidth.Byte.asUInt -> Mux1H(
            (0 until 8).map(i =>
              (addressLower === i.U) -> (entry.data(7, 0) << i * 8)
            )
          ),
          MemoryAccessWidth.HalfWord.asUInt -> Mux1H(
            (0 until 4).map(i =>
              (addressLower(2, 1) === i.U) -> (entry.data(15, 0) << i * 16)
            )
          ),
          MemoryAccessWidth.Word.asUInt -> Mux1H(
            (0 until 2).map(i =>
              (addressLower(2) === i.U) -> (entry.data(31, 0) << i * 32)
            )
          ),
          MemoryAccessWidth.DoubleWord.asUInt -> entry.data
        )
      )
      io.dataWriteRequest.bits.mask := MuxLookup(
        entry.accessInfo.accessWidth.asUInt,
        0.U,
        Seq(
          MemoryAccessWidth.Byte.asUInt -> Mux1H(
            (0 until 8).map(i => (addressLower === i.U) -> (1 << i).U)
          ),
          MemoryAccessWidth.HalfWord.asUInt -> Mux1H(
            (0 until 4).map(i => (addressLower(2, 1) === i.U) -> (3 << i * 2).U)
          ),
          MemoryAccessWidth.Word.asUInt -> Mux1H(
            (0 until 2).map(i => (addressLower(2) === i.U) -> (15 << i * 4).U)
          ),
          MemoryAccessWidth.DoubleWord.asUInt -> "b11111111".U
        )
      )
      io.dataReadRequest.bits.outputTag := entry.tag
      when(io.dataWriteRequest.ready) {
        buffer.output.ready := true.B
      }
    }
  }
}

object DataMemoryBuffer extends App {
  implicit val params = Parameters(tagWidth = 4, maxRegisterFileCommitCount = 2)
  (new ChiselStage).emitVerilog(
    new DataMemoryBuffer,
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
