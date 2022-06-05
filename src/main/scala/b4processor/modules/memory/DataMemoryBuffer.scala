package b4processor.modules.memory

import b4processor.Parameters
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

  val defaultEntry = {
    val entry = Wire(new DataMemoryBufferEntry)
    entry.address := 0.S
    entry.tag := 0.U
    entry.data := 0.U
    entry.opcode := 0.U
    entry.function3 := 0.U
    entry
  }

  val head = RegInit(0.U(params.tagWidth.W))
  val tail = RegInit(0.U(params.tagWidth.W))
  val buffer = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt)(defaultEntry)))
  var insertIndex = head

  // enqueue
  for(i <- 0 until params.maxLSQ2MemoryinstCount) {
    val Input = io.dataIn(i)
    Input.ready := true.B

    when(Input.valid) {
      buffer(insertIndex) := {
        val entry = Wire(new DataMemoryBufferEntry)
        entry.address := Input.bits.address
        entry.tag := Input.bits.tag
        entry.data := Input.bits.data
        entry.opcode := Input.bits.opcode
        entry.function3 := Input.bits.function3
        entry
      }
    }
    insertIndex = Mux(insertIndex === (math.pow(2, params.tagWidth).toInt.U-1.U) && Input.valid, 0.U, insertIndex + Input.valid.asUInt)
  }

  head := insertIndex

  // dequeue
  var emissionIndex = tail
  when(io.dataOut.ready && tail === head) {
    io.dataOut.valid := true.B
    io.dataOut.bits.address := buffer(tail).address
    io.dataOut.bits.tag := buffer(tail).tag
    io.dataOut.bits.data := buffer(tail).data
    io.dataOut.bits.opcode := buffer(tail).opcode
    io.dataOut.bits.function3 := buffer(tail).function3
  }.otherwise {
    io.dataOut.valid := false.B
    io.dataOut.bits.address := 0.S
    io.dataOut.bits.tag := 0.U
    io.dataOut.bits.data := 0.U
    io.dataOut.bits.opcode := 0.U
    io.dataOut.bits.function3 := 0.U
  }
  tail := Mux(tail === (math.pow(2, params.tagWidth).toInt.U-1.U) && io.dataOut.valid,
    0.U, tail + io.dataOut.valid.asUInt)
}

object DataMemoryBuffer extends App {
  implicit val params = Parameters(tagWidth = 4, maxLSQ2MemoryinstCount = 2)
  (new ChiselStage).emitVerilog(new DataMemoryBuffer, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
