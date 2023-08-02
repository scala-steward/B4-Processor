package b4processor.modules.cache

import circt.stage.ChiselStage
import b4processor.Parameters
import b4processor.connections.LoadStoreQueue2Memory
import b4processor.modules.memory.{
  MemoryReadIntent,
  MemoryReadTransaction,
  MemoryWriteTransaction
}
import b4processor.structures.memoryAccess.{MemoryAccessType, MemoryAccessWidth}
import b4processor.utils.operations.{LoadStoreOperation, LoadStoreWidth}
import b4processor.utils.{B4RRArbiter, FIFO}
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
//  buffer.flush := false.B

  io.dataReadRequest.bits := 0.U.asTypeOf(new MemoryReadTransaction)
  io.dataReadRequest.valid := false.B
  io.dataWriteRequest.bits := 0.U.asTypeOf(new MemoryWriteTransaction)
  io.dataWriteRequest.valid := false.B

  when(!buffer.empty) {
    val entry = buffer.output.bits

    val operationIsStore = LoadStoreOperation.Store === entry.operation

    when(!operationIsStore) {
      io.dataReadRequest.valid := true.B
      val size = MuxLookup(entry.operationWidth, MemoryAccessWidth.DoubleWord)(
        Seq(
          LoadStoreWidth.Byte -> MemoryAccessWidth.Byte,
          LoadStoreWidth.HalfWord -> MemoryAccessWidth.HalfWord,
          LoadStoreWidth.Word -> MemoryAccessWidth.Word,
          LoadStoreWidth.DoubleWord -> MemoryAccessWidth.DoubleWord
        )
      )
      val signed = LoadStoreOperation.Load === entry.operation
      io.dataReadRequest.bits := MemoryReadTransaction.ReadToTag(
        entry.address,
        size,
        signed,
        entry.tag
      )
      when(io.dataReadRequest.ready) {
        buffer.output.ready := true.B
      }
    }.elsewhen(operationIsStore) {
      io.dataWriteRequest.valid := true.B
      val addressUpper = entry.address(63, 3)
      val addressLower = entry.address(2, 0)
      io.dataWriteRequest.bits.address := addressUpper ## 0.U(3.W)
      io.dataWriteRequest.bits.outputTag := entry.tag
      io.dataWriteRequest.bits.data := MuxLookup(entry.operationWidth, 0.U)(
        Seq(
          LoadStoreWidth.Byte -> Mux1H(
            (0 until 8).map(i =>
              (addressLower === i.U) -> (entry.data(7, 0) << i * 8).asUInt
            )
          ),
          LoadStoreWidth.HalfWord -> Mux1H(
            (0 until 4).map(i =>
              (addressLower(2, 1) === i.U) -> (entry
                .data(15, 0) << i * 16).asUInt
            )
          ),
          LoadStoreWidth.Word -> Mux1H(
            (0 until 2).map(i =>
              (addressLower(2) === i.U) -> (entry.data(31, 0) << i * 32).asUInt
            )
          ),
          LoadStoreWidth.DoubleWord -> entry.data
        )
      )
      io.dataWriteRequest.bits.mask := MuxLookup(entry.operationWidth, 0.U)(
        Seq(
          LoadStoreWidth.Byte -> Mux1H(
            (0 until 8).map(i => (addressLower === i.U) -> (1 << i).U)
          ),
          LoadStoreWidth.HalfWord -> Mux1H(
            (0 until 4).map(i => (addressLower(2, 1) === i.U) -> (3 << i * 2).U)
          ),
          LoadStoreWidth.Word -> Mux1H(
            (0 until 2).map(i => (addressLower(2) === i.U) -> (15 << i * 4).U)
          ),
          LoadStoreWidth.DoubleWord -> "b11111111".U
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
