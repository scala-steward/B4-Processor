package b4processor.utils

import chisel3._
import chisel3.experimental.FlatIO
import chisel3.stage.ChiselStage
import chisel3.util.Valid

class SimpleAXIMemory(size: Int = 1024) extends Module {
  val axi = FlatIO(Flipped(new AXI(64, 64)))
  val simulationSource = IO(new Bundle {
    val input = Flipped(Valid(UInt(64.W)))
  })

  locally {
    import axi._
    read.bits.USER := 0.U
    readAddress.ready := false.B
    read.valid := false.B
    writeResponse.valid := false.B
    writeResponse.bits.ID := 0.U
    read.bits.LAST := false.B
    write.ready := false.B
    read.bits.DATA := 0.U
    read.bits.RESP := Response.Okay
    read.bits.ID := 0.U
    writeAddress.ready := false.B
    writeResponse.bits.USER := 0.U
    writeResponse.bits.RESP := Response.Okay
  }

  val mem = Seq.fill(8)(SyncReadMem(size, UInt(8.W)))

  val sourceReady = RegInit(false.B)
  val gotSize = RegInit(false.B)
  val sourceSize = RegInit("xFFFFFFFF".U)
  val sourceWriteIndex = Reg(UInt(32.W))

  when(!sourceReady) {
    when(!gotSize) {
      when(simulationSource.input.valid) {
        sourceSize := simulationSource.input.bits
        sourceWriteIndex := 0.U
        gotSize := true.B
      }
    }.otherwise {
      when(simulationSource.input.valid) {
        for (i <- 0 until 8) {
          mem(i).write(
            sourceWriteIndex,
            simulationSource.input.bits(i * 8 + 7, i * 8)
          )
        }
        sourceWriteIndex := sourceWriteIndex + 1.U
      }
    }
    when(sourceWriteIndex === sourceSize) {
      sourceReady := true.B
    }
  }

  // WRITE OPERATION
  val writeState = Module(new FIFO(2)(new Bundle {
    val address = UInt(64.W)
    val burstLength = UInt(8.W)
  }))
  writeState.input.valid := false.B
  writeState.output.ready := false.B
  writeState.input.bits := DontCare

  val writeResponseState = Module(new FIFO(2)(new Bundle() {
    val isError = Bool()
  }))
  writeResponseState.input.valid := false.B
  writeResponseState.output.ready := false.B
  writeResponseState.input.bits := DontCare

  val burstLen = RegInit(0.U(8.W))

  when(sourceReady) {
    when(!writeState.full) {
      axi.writeAddress.ready := true.B
      when(axi.writeAddress.valid) {
        writeState.input.valid := true.B
        writeState.input.bits.address := axi.writeAddress.bits.ADDR
        writeState.input.bits.burstLength := axi.writeAddress.bits.LEN
      }
    }
    when(!writeState.empty) {
      axi.write.ready := true.B
      when(axi.write.valid) {
        for (i <- 0 until 8) {
          mem(i).write(
            writeState.output.bits.address(63, 3) + burstLen,
            axi.write.bits.DATA(i * 8 + 7, i * 8)
          )
        }
        burstLen := burstLen + 1.U
        when(burstLen === writeState.output.bits.burstLength) {
          burstLen := 0.U
          writeState.output.ready := true.B
          writeResponseState.input.valid := true.B
          writeResponseState.input.bits.isError := false.B
        }
      }
    }
    when(!writeResponseState.empty) {
      axi.writeResponse.valid := true.B
      axi.writeResponse.bits.RESP := Response.Okay
      when(axi.writeResponse.ready) {
        writeResponseState.output.ready := true.B
      }
    }
  }

  // READ OPERATION
  val readState = Module(new FIFO(2)(new Bundle {
    val address = UInt(64.W)
    val burstLength = UInt(8.W)
  }))
  readState.input.valid := false.B
  readState.output.ready := false.B
  readState.input.bits := DontCare

  val readBurstLen = RegInit(0.U(8.W))
  val readDone = RegInit(false.B)

  when(sourceReady) {
    when(!readState.full) {
      axi.readAddress.ready := true.B
      when(axi.readAddress.valid) {
        readState.input.valid := true.B
        readState.input.bits.address := axi.readAddress.bits.ADDR
        readState.input.bits.burstLength := axi.readAddress.bits.LEN
      }
    }

    when(!readState.empty) {
      val rwport = (0 until 8).map(i =>
        mem(i)(
          readState.output.bits
            .address(63, 3) + readBurstLen + Mux(
            axi.read.ready && readDone,
            1.U,
            0.U
          )
        )
      )
      when(!readDone) {
        axi.read.bits.DATA := (0 until 8).reverse
          .map(i => rwport(i))
          .reduce(_ ## _)
        readDone := true.B
      }.otherwise {
        axi.read.valid := true.B
        when(axi.read.ready) {
          axi.read.bits.DATA := (0 until 8).reverse
            .map(i => rwport(i))
            .reduce(_ ## _)
          readBurstLen := readBurstLen + 1.U
          when(readBurstLen === readState.output.bits.burstLength) {
            axi.read.bits.LAST := true.B
            readState.output.ready := true.B
            readDone := false.B
            readBurstLen := 0.U
          }
        }.otherwise {
          axi.read.bits.DATA := (0 until 8).reverse
            .map(i => rwport(i))
            .reduce(_ ## _)
        }
      }
    }
  }
}

object SimpleAXIMemory extends App {
  (new ChiselStage).emitVerilog(new SimpleAXIMemory)
}
