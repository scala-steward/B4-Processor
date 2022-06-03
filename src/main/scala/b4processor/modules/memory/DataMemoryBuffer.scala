package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections
import b4processor.connections.LoadStoreQueue2Memory
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

/**
 * from LSQ toDataMemory のためのバッファ
 *
 * @param params パラメータ
 */
class DataMemoryBuffer(implicit  params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn = Vec(params.maxLSQ2MemoryinstCount, Flipped(new LoadStoreQueue2Memory))
    val dataOut = new LoadStoreQueue2Memory
    val head = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
    val tail = if (params.debug) Some(Output(UInt(params.tagWidth.W))) else None
  })

  // enqueue

  // dequeue
}
