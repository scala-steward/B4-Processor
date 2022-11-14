package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.LoadStoreQueue2Memory
import b4processor.modules.memory.{
  MemoryReadTransaction,
  MemoryWriteTransaction
}
import b4processor.structures.memoryAccess.{
  MemoryAccessInfo,
  MemoryAccessType,
  MemoryAccessWidth
}
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
      Vec(params.maxRegisterFileCommitCount, Flipped(new LoadStoreQueue2Memory))
    val dataReadRequest = Irrevocable(new MemoryReadTransaction())
    val dataWriteRequest = Irrevocable(new MemoryWriteTransaction())
    val head =
      if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W)))
      else None
    val tail =
      if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W)))
      else None
  })

  // isLoad = 1(load), 0(store) (bit数削減)
  val defaultEntry = DataMemoryBufferEntry.default

  val head = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  val tail = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  private val empty = tail === head
  private val full = head + 1.U === tail
  val buffer = RegInit(
    VecInit(
      Seq.fill(math.pow(2, params.loadStoreQueueIndexWidth).toInt)(defaultEntry)
    )
  )

  var insertIndex = head
  // enqueue
  for (i <- 0 until params.maxRegisterFileCommitCount) {
    val Input = io.dataIn(i)
    Input.ready := tail =/= insertIndex + 1.U

    when(Input.valid) {
      buffer(insertIndex) := DataMemoryBufferEntry.validEntry(
        address = Input.bits.address,
        tag = Input.bits.tag,
        data = Input.bits.data,
        accessInfo = Input.bits.accessInfo
      )
    }
    insertIndex = insertIndex + Input.valid.asUInt
  }
  head := insertIndex

  io.dataReadRequest.bits := DontCare
  io.dataReadRequest.valid := false.B
  io.dataWriteRequest.bits := DontCare
  io.dataWriteRequest.valid := false.B

  when(!empty) {
    val entry = buffer(tail)
    when(entry.accessInfo.accessType === MemoryAccessType.Load) {
      io.dataReadRequest.valid := true.B
      io.dataReadRequest.bits.address := entry.address
      io.dataReadRequest.bits.size := entry.accessInfo.accessWidth
      io.dataReadRequest.bits.outputTag := entry.tag
      when(io.dataReadRequest.ready) {
        tail := tail + 1.U
      }
    }.elsewhen(buffer(tail).accessInfo.accessType === MemoryAccessType.Store) {
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
      when(io.dataReadRequest.ready) {
        tail := tail + 1.U
      }
    }
  }

  if (params.debug) {
    io.head.get := head
    io.tail.get := tail
    //    for (i <- 0 until params.maxRegisterFileCommitCount) {
    //      printf(p"io.dataIn(${i}).valid = ${io.dataIn(i).valid}\n")
    //    }
    //    for (i <- 0 until pow(2, params.loadStoreQueueIndexWidth).toInt) {
    //      printf(p"buffer(${i}).tag = ${buffer(i).tag}\n")
    //    }
    //    printf(p"io.dataOut.ready = ${io.dataOut.ready}\n")
    //    printf(p"io.dataOut.valid = ${io.dataOut.valid}\n")
    //    printf(p"io.dataOut.tag = ${io.dataOut.bits.tag}\n")
    //    printf(p"head = ${head}\n")
    //    printf(p"tail = ${tail}\n\n")
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
