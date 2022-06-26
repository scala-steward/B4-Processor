package b4processor.modules.memory

import b4processor.Parameters
import b4processor.connections.{LoadStoreQueue2Memory, OutputValue}
import chisel3.{RegNext, _}
import chisel3.util._
import chisel3.stage.ChiselStage

class DataMemory(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val dataIn = Flipped(new LoadStoreQueue2Memory)
    val dataOut = new OutputValue
  })

  val LOAD = "b0000011".U
  val STORE = "b0100011".U


  val mem = SyncReadMem(params.dataMemorySize, UInt(64.W))
  io.dataOut := DontCare

  io.dataIn.ready := RegNext(io.dataIn.bits.isLoad)
  io.dataOut.value := 0.U
  val nextLoad = RegNext(io.dataIn.valid && io.dataIn.bits.isLoad)
  when(io.dataIn.valid || nextLoad) {
    // FIXME: アドレスを下位28bitのみ使っている
    val rdwrPort = mem(io.dataIn.bits.address.asUInt(27, 0))
    when(io.dataIn.valid && !io.dataIn.bits.isLoad) {
      // printf(p"dataIn =${io.dataIn.bits.data}\n")
      // Store
      /** writeの場合，rdwrPortは命令実行時の次クロック立ち上がりでmemoryに書き込み(=ストア命令実行時では値変わらず) */
      rdwrPort := MuxLookup(io.dataIn.bits.function3, 0.U, Seq(
        "b000".U -> Mux(io.dataIn.bits.data(7), Cat(~0.U(56.W), io.dataIn.bits.data(7, 0)), Cat(0.U(56.W), io.dataIn.bits.data(7, 0))),
        "b001".U -> Mux(io.dataIn.bits.data(15), Cat(~0.U(48.W), io.dataIn.bits.data(15, 0)), Cat(0.U(48.W), io.dataIn.bits.data(15, 0))),
        "b010".U -> Mux(io.dataIn.bits.data(31), Cat(~0.U(32.W), io.dataIn.bits.data(31, 0)), Cat(0.U(32.W), io.dataIn.bits.data(31, 0))),
        "b011".U -> io.dataIn.bits.data
      ))
    }.otherwise {


      /** readの場合，rdwrPortは命令実行時と同クロック立ち上がりでmemoryから読み込み(=ロード命令実行時に値変更) */
      // printf(p"rdwrPort(7) = ${rdwrPort(7)}\n")

    }
    // Load 出てくる出力が1クロック遅れているのでRegNextを使う
    when(RegNext(io.dataIn.bits.isLoad)) {
      io.dataOut.value := MuxLookup(RegNext(io.dataIn.bits.function3), 0.U, Seq(
        "b000".U -> Mux(rdwrPort(7), Cat(~0.U(56.W), rdwrPort(7, 0)), Cat(0.U(56.W), rdwrPort(7, 0))),
        "b001".U -> Mux(rdwrPort(15), Cat(~0.U(48.W), rdwrPort(15, 0)), Cat(0.U(48.W), rdwrPort(15, 0))),
        "b010".U -> Mux(rdwrPort(31), Cat(~0.U(32.W), rdwrPort(31, 0)), Cat(0.U(32.W), rdwrPort(31, 0))),
        "b011".U -> rdwrPort,
        "b100".U -> rdwrPort(7, 0),
        "b101".U -> rdwrPort(15, 0),
        "b110".U -> rdwrPort(31, 0),
      ))
    }
    printf(p"rdwrPort =${rdwrPort}\n")
    // printf(p"rdwrPort(7, 0) = ${rdwrPort(7, 0)}\n")
    printf(p"dataOut = ${io.dataOut.value}\n")
  }
  printf(p"io.dataOut.validasResult = ${io.dataOut.validAsResult}\n")
  printf(p"io.dataOut.tag = ${io.dataOut.tag}\n")
  printf(p"io.dataOut.value = ${io.dataOut.value}\n\n")
  io.dataOut.tag := RegNext(Mux(io.dataIn.bits.isLoad, io.dataIn.bits.tag, 0.U))
  io.dataOut.validAsResult := RegNext(io.dataIn.bits.isLoad)
  io.dataOut.validAsLoadStoreAddress := RegNext(io.dataIn.bits.isLoad)
  // printf(p"mem(io.dataIn.bits.address.asUInt) = ${mem(io.dataIn.bits.address.asUInt)}\n")
}


object DataMemoryElaborate extends App {
  implicit val params = Parameters(runParallel = 1, maxRegisterFileCommitCount = 1, tagWidth = 4)
  (new ChiselStage).emitVerilog(new DataMemory, args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
