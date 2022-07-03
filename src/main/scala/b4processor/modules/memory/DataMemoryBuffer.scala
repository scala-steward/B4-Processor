package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.LoadStoreQueue2Memory
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

/**
 * from LSQ toDataMemory のためのバッファ
 *
 * @param params パラメータ
 */
class DataMemoryBuffer(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn = Vec(params.maxRegisterFileCommitCount, Flipped(new LoadStoreQueue2Memory))
    val dataOut = new LoadStoreQueue2Memory
    val head = if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.loadStoreQueueIndexWidth.W))) else None
  })

  // isLoad = 1(load), 0(store) (bit数削減)
  val defaultEntry = DataMemoryBufferEntry.default

  val head = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  val tail = RegInit(0.U(params.loadStoreQueueIndexWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.loadStoreQueueIndexWidth).toInt)(defaultEntry)))

  var insertIndex = head
  // enqueue
  for (i <- 0 until params.maxRegisterFileCommitCount) {
    val Input = io.dataIn(i)
    Input.ready := tail =/= insertIndex + 1.U

    when(Input.valid) {
      buffer(insertIndex) := DataMemoryBufferEntry.validEntry(
        address = Input.bits.address, tag = Input.bits.tag,
        data = Input.bits.data, isLoad = Input.bits.isLoad,
        function3 = Input.bits.function3)
    }
    insertIndex = Mux(insertIndex === (math.pow(2, params.tagWidth - 2).toInt.U - 1.U) && Input.valid, 0.U, insertIndex + Input.valid.asUInt)
  }
  head := insertIndex

  io.dataOut.bits.address := 0.S
  io.dataOut.bits.tag := 0.U
  io.dataOut.bits.data := 0.U
  io.dataOut.bits.isLoad := false.B
  io.dataOut.bits.function3 := 0.U
  io.dataOut.valid := io.dataOut.ready && tail =/= head
  // dequeue
  when(tail =/= head) {
    io.dataOut.bits.address := buffer(tail).address
    io.dataOut.bits.tag := buffer(tail).tag
    io.dataOut.bits.data := buffer(tail).data
    io.dataOut.bits.isLoad := buffer(tail).isLoad
    io.dataOut.bits.function3 := buffer(tail).function3
    buffer(tail) := DataMemoryBufferEntry.default
  }

  tail := Mux(tail === (math.pow(2, params.loadStoreQueueIndexWidth).toInt.U - 1.U) && io.dataOut.valid,
    0.U, tail + io.dataOut.valid.asUInt)

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
  (new ChiselStage).emitVerilog(new DataMemoryBuffer, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
