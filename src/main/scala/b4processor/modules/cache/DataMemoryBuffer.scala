package b4processor.modules.cache

import circt.stage.ChiselStage
import b4processor.Parameters
import b4processor.connections.LoadStoreQueue2Memory
import b4processor.modules.memory.{
  MemoryReadTransaction,
  MemoryWriteTransaction
}
import b4processor.structures.memoryAccess.{MemoryAccessType, MemoryAccessWidth}
import b4processor.utils.{B4RRArbiter, FIFO, LoadStoreOperation}
import chisel3._
import chisel3.util._

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
    new B4RRArbiter(new DataMemoryBufferEntry, params.threads)
  )
  for (tid <- 0 until params.threads)
    inputArbiter.io.in(tid) <> io.dataIn(tid)

  private val buffer = Module(
    new FIFO(params.loadStoreQueueIndexWidth)(new DataMemoryBufferEntry)
  )
  buffer.input <> inputArbiter.io.out
  buffer.output.ready := false.B
  buffer.flush := false.B

  io.dataReadRequest.bits := DontCare
  io.dataReadRequest.valid := false.B
  io.dataWriteRequest.bits := DontCare
  io.dataWriteRequest.valid := false.B

  when(!buffer.empty) {
    val entry = buffer.output.bits

    val operationIsStore = Seq(
      LoadStoreOperation.Store8,
      LoadStoreOperation.Store16,
      LoadStoreOperation.Store32,
      LoadStoreOperation.Store64
    ).map(_ === entry.operation).reduce(_ || _)

    when(!operationIsStore) {
      io.dataReadRequest.valid := true.B
      io.dataReadRequest.bits.address := entry.address
      io.dataReadRequest.bits.size := MuxLookup(
        entry.operation,
        MemoryAccessWidth.DoubleWord
      )(
        Seq(
          LoadStoreOperation.Load8 -> MemoryAccessWidth.Byte,
          LoadStoreOperation.Load16 -> MemoryAccessWidth.HalfWord,
          LoadStoreOperation.Load32 -> MemoryAccessWidth.Word,
          LoadStoreOperation.Load64 -> MemoryAccessWidth.DoubleWord,
          LoadStoreOperation.Load8Unsigned -> MemoryAccessWidth.Byte,
          LoadStoreOperation.Load16Unsigned -> MemoryAccessWidth.HalfWord,
          LoadStoreOperation.Load32Unsigned -> MemoryAccessWidth.Word
        )
      )
      io.dataReadRequest.bits.outputTag := entry.tag
      val signed = Seq(
        LoadStoreOperation.Load8,
        LoadStoreOperation.Load16,
        LoadStoreOperation.Load32
      ).map(_ === entry.operation).reduce(_ || _)
      io.dataReadRequest.bits.signed := signed
      when(io.dataReadRequest.ready) {
        buffer.output.ready := true.B
      }
    }.elsewhen(operationIsStore) {
      io.dataWriteRequest.valid := true.B
      val addressUpper = entry.address(63, 3)
      val addressLower = entry.address(2, 0)
      io.dataWriteRequest.bits.address := addressUpper ## 0.U(3.W)
      io.dataWriteRequest.bits.outputTag := entry.tag
      io.dataWriteRequest.bits.data := MuxLookup(entry.operation, 0.U)(
        Seq(
          LoadStoreOperation.Store8 -> Mux1H(
            (0 until 8).map(i =>
              (addressLower === i.U) -> (entry.data(7, 0) << i * 8).asUInt
            )
          ),
          LoadStoreOperation.Store16 -> Mux1H(
            (0 until 4).map(i =>
              (addressLower(2, 1) === i.U) -> (entry
                .data(15, 0) << i * 16).asUInt
            )
          ),
          LoadStoreOperation.Store32 -> Mux1H(
            (0 until 2).map(i =>
              (addressLower(2) === i.U) -> (entry.data(31, 0) << i * 32).asUInt
            )
          ),
          LoadStoreOperation.Store64 -> entry.data
        )
      )
      io.dataWriteRequest.bits.mask := MuxLookup(entry.operation, 0.U)(
        Seq(
          LoadStoreOperation.Store8 -> Mux1H(
            (0 until 8).map(i => (addressLower === i.U) -> (1 << i).U)
          ),
          LoadStoreOperation.Store16 -> Mux1H(
            (0 until 4).map(i => (addressLower(2, 1) === i.U) -> (3 << i * 2).U)
          ),
          LoadStoreOperation.Store32 -> Mux1H(
            (0 until 2).map(i => (addressLower(2) === i.U) -> (15 << i * 4).U)
          ),
          LoadStoreOperation.Store64 -> "b11111111".U
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
  ChiselStage.emitSystemVerilogFile(new DataMemoryBuffer)
}
