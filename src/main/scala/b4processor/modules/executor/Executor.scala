package b4processor.modules.executor

import b4processor.Parameters
import b4processor.common.{ArithmeticOperations, BranchOperations, InstructionChecker, Instructions}
import b4processor.connections._
import chisel3.{Mux, _}
import chisel3.util._
import chisel3.stage.ChiselStage

class Executor(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reservationstation = Flipped(new ReservationStation2Executor)
    val reorderBuffer = new ExecutionRegisterBypass
    val decoders = Vec(params.numberOfDecoders, new ExecutionRegisterBypass)
    val loadstorequeue = Output(new Executor2LoadStoreQueue)
    val fetch = new Executor2Fetch
  })

  /**
   * リザベーションステーションから実行ユニットへデータを送信
   * op,fuctionによって命令を判断し，計算を実行
   */
  io.reservationstation.ready := true.B
  val instructionChecker = Module(new InstructionChecker)

  instructionChecker.input.opcode := io.reservationstation.bits.opcode
  instructionChecker.input.function3bits := io.reservationstation.bits.function3
  instructionChecker.input.function7bits := io.reservationstation.bits.immediateOrFunction7

  val destinationRegister = Wire(UInt(64.W))
  val immediateOrFunction7Extended = Mux(io.reservationstation.bits.immediateOrFunction7(11), (!0.U(64.W)) & io.reservationstation.bits.immediateOrFunction7, 0.U(64.W) | io.reservationstation.bits.immediateOrFunction7)
  val newProgramCounter = io.reservationstation.bits.programCounter + immediateOrFunction7Extended.asSInt

  // set destinationRegister
  io.reservationstation.ready := true.B
  when(io.reservationstation.valid === true.B) {
    destinationRegister := MuxCase(0.U, Seq(
      // 加算
      (instructionChecker.output.instruction === Instructions.Load || (instructionChecker.output.arithmetic === ArithmeticOperations.Addition))
        -> (io.reservationstation.bits.value1 + io.reservationstation.bits.value2),
      // 減算
      (instructionChecker.output.arithmetic === ArithmeticOperations.Subtract)
        -> (io.reservationstation.bits.value1 - io.reservationstation.bits.value2),
      // 論理積
      (instructionChecker.output.arithmetic === ArithmeticOperations.And)
        -> (io.reservationstation.bits.value1 & io.reservationstation.bits.value2),
      // 論理和
      (instructionChecker.output.arithmetic === ArithmeticOperations.Or)
        -> (io.reservationstation.bits.value1 | io.reservationstation.bits.value2),
      // 排他的論理和
      (instructionChecker.output.arithmetic === ArithmeticOperations.Xor)
        -> (io.reservationstation.bits.value1 ^ io.reservationstation.bits.value2),
      // 左シフト
      (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftLeftLogical)
        -> (io.reservationstation.bits.value1 << io.reservationstation.bits.value2.tail(6: Int)),
      // 右シフト(論理)
      (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftRightLogical)
        -> (io.reservationstation.bits.value1 >> io.reservationstation.bits.value2.tail(6: Int)),
      //右シフト(算術)
      (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftRightArithmetic)
        -> (io.reservationstation.bits.value1.asSInt >> io.reservationstation.bits.value2.tail(6: Int)).asUInt,
      // Cat(0.U((io.reservationstation.bits.value2.tail(6: Int).W)), io.reservationstation.bits.value1.head(6: Int))
      // 比較(格納先：rd)(符号付き)
      (instructionChecker.output.arithmetic === ArithmeticOperations.SetLessThan)
        -> (io.reservationstation.bits.value1.asSInt < io.reservationstation.bits.value2.asSInt).asUInt,
      // 比較(格納先：rd)(符号なし)
      (instructionChecker.output.arithmetic === ArithmeticOperations.SetLessThanUnsigned)
        -> (io.reservationstation.bits.value1 < io.reservationstation.bits.value2),
      // 無条件ジャンプ
      (instructionChecker.output.instruction === Instructions.jal || instructionChecker.output.instruction === Instructions.jalr)
        -> (io.reservationstation.bits.programCounter.asUInt + 4.U),
      // lui
      (instructionChecker.output.instruction === Instructions.lui)
        -> io.reservationstation.bits.value2,
      // auipc
      (instructionChecker.output.instruction === Instructions.auipc)
        -> (io.reservationstation.bits.value2 + io.reservationstation.bits.programCounter.asUInt)
    ))

    io.fetch.bits.programCounter := MuxCase(io.reservationstation.bits.programCounter, Seq(
      // 分岐
      // Equal
      (instructionChecker.output.branch === BranchOperations.Equal)
        -> Mux(io.reservationstation.bits.value1 === io.reservationstation.bits.value2,
        newProgramCounter, 0.S),
      // NotEqual
      (instructionChecker.output.branch === BranchOperations.NotEqual)
        -> Mux(io.reservationstation.bits.value1 =/= io.reservationstation.bits.value2,
        newProgramCounter, 0.S),
      // Less Than (signed)
      (instructionChecker.output.branch === BranchOperations.LessThan)
        -> Mux(io.reservationstation.bits.value1.asSInt < io.reservationstation.bits.value2.asSInt,
        newProgramCounter, 0.S),
      // Less Than (unsigned)
      (instructionChecker.output.branch === BranchOperations.LessThanUnsigned)
        -> Mux(io.reservationstation.bits.value1 < io.reservationstation.bits.value2,
        newProgramCounter, 0.S),
      // Greater Than (signed)
      (instructionChecker.output.branch === BranchOperations.GreaterOrEqual)
        -> Mux(io.reservationstation.bits.value1.asSInt >= io.reservationstation.bits.value2.asSInt,
        newProgramCounter, 0.S),
      // Greater Than (unsigned)
      (instructionChecker.output.branch === BranchOperations.GreaterOrEqualUnsigned)
        -> Mux(io.reservationstation.bits.value1 >= io.reservationstation.bits.value2,
        newProgramCounter, 0.S),
      (instructionChecker.output.instruction === Instructions.auipc)
        -> (io.reservationstation.bits.programCounter + io.reservationstation.bits.value2.asSInt),
    ))
    // S形式(LSQへアドレスを渡す)
    io.loadstorequeue.ProgramCounter := io.reservationstation.bits.programCounter
    io.loadstorequeue.destinationTag := io.reservationstation.bits.destinationTag
    io.loadstorequeue.value := Mux(instructionChecker.output.instruction === Instructions.Store,
      io.reservationstation.bits.value1 + immediateOrFunction7Extended, destinationRegister)
    io.loadstorequeue.valid := instructionChecker.output.instruction =/= Instructions.Unknown
  }

  /**
   * 実行結果をリオーダバッファ,デコーダに送信
   * (validで送信データを調節)
   * (レジスタ挿入の可能性あり)
   */
  // reorder Buffer
  io.reorderBuffer.valid := instructionChecker.output.instruction =/= Instructions.Unknown
  io.reorderBuffer.destinationTag := io.reservationstation.bits.destinationTag
  io.reorderBuffer.value := destinationRegister

  // decoders
  for (i <- 0 until params.numberOfALUs) {
    io.decoders(i).valid := instructionChecker.output.instruction =/= Instructions.Unknown
    io.decoders(i).destinationTag := io.reservationstation.bits.destinationTag
    io.decoders(i).value := destinationRegister
  }
}

object ExecutorElaborate extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new Executor(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
