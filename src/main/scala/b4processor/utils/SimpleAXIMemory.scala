package b4processor.utils

import chisel3._
import chisel3.experimental.FlatIO
import chisel3.util._

class SimpleAXIMemory(size: Int = 1024) extends Module {
  val axiIo = FlatIO(Flipped(new AXI(64)))


  // WRITE OPERATION
  val writeState = Module(new FIFO(2)(new Bundle {
    val address = UInt(64.W)
    val burstLength = UInt(8.W)
  }))

  val writeResponseState = Module(new FIFO(2)(new Bundle() {
    val isError = Bool()
  }))

  when(!writeState.full) {
    axiIo.writeAddress.ready := true.B
    when(axiIo.writeAddress.valid) {
      writeState.input.bits.address := axiIo.writeAddress.bits.ADDR
      writeState.input.bits.burstLength := axiIo.writeAddress.bits.LEN
    }
  }
  when(!writeState.empty) {
    axiIo.write.ready := true.B

  }

  val globalAddress = Reg(UInt(64.W))
  val readData = Reg(UInt(64.W))
  //  val strb = Reg(UInt(8.W))

  val mem = Seq.fill(8)(SyncReadMem(size, UInt(8.W)))

  switch(state) {
    is(stateWaitForRequest) {
      axiIo.write.ready := true.B
      axiIo.writeAddress.ready := true.B
      when(axiIo.writeAddress.valid && axiIo.writeAddress.valid) {
        val address = axiIo.writeAddress.bits.ADDR
        val writeData = axiIo.write.bits.DATA
        val strb = axiIo.write.bits.STRB

        val internalAddress = address(63, 4)
        for (i <- 0 until 8) {
          when(strb(i)) {
            mem(i).write(internalAddress, writeData(8 * i + 7, 8 * i))
          }
        }
        axiIo.writeResponse.valid := true.B
        when(!axiIo.writeResponse.ready) {
          state := stateWriteResponse
        }
      }.elsewhen(axiIo.readAddress.valid) {
        val address = axiIo.writeAddress.bits.ADDR
        globalAddress := address
        val internalAddress = address(63, 4)

        for (i <- 0 until 8) {
          mem(i).read(internalAddress)
        }
        state := stateReadMemGetValue
      }

      assert(
        axiIo.writeAddress.valid && axiIo.writeAddress.valid && axiIo.readAddress.valid
      )
    }

    is(stateWriteResponse) {
      axiIo.writeResponse.valid := true.B
      when(axiIo.writeResponse.ready) {
        state := stateWaitForRequest
      }
    }

    is(stateReadMemGetValue) {
      val internalAddress = globalAddress(63, 4)
      for (i <- 0 until 8) {
        mem(i).read(internalAddress)
      }
    }
  }
}
