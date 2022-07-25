package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.LoadStoreQueue2Memory
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

/** from LSQ toDataMemory のためのバッファ
  *
  * @param params
  *   パラメータ
  */
class DataMemoryBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn =
      Vec(params.maxRegisterFileCommitCount, Flipped(new LoadStoreQueue2Memory))
    val dataOut = new LoadStoreQueue2Memory
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

  io.dataOut.bits := DontCare
  io.dataOut.valid := io.dataOut.ready && tail =/= head
  // dequeue
  when(io.dataOut.valid) {
    io.dataOut.bits.address := buffer(tail).address
    io.dataOut.bits.tag := buffer(tail).tag
    io.dataOut.bits.data := buffer(tail).data
    io.dataOut.bits.accessInfo := buffer(tail).accessInfo
    buffer(tail) := DataMemoryBufferEntry.default
  }

  tail := tail + io.dataOut.valid.asUInt

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
