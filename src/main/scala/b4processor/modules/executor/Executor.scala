package b4processor.modules.executor

import b4processor.Parameters
import b4processor.common.{ArithmeticOperations, BranchOperations, InstructionChecker, Instructions, OperationWidth}
import b4processor.connections._
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.{Mux, _}

class Executor(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reservationStation = Flipped(new ReservationStation2Executor)
    val out = new ExecutionRegisterBypass
    val loadStoreQueue = Output(new Executor2LoadStoreQueue)
    val fetch = Output(new Executor2Fetch)
  })

  /**
   * リザベーションステーションから実行ユニットへデータを送信
   * op,fuctionによって命令を判断し，計算を実行
   */
  io.reservationStation.ready := true.B
  val instructionChecker = Module(new InstructionChecker)

  instructionChecker.input.opcode := io.reservationStation.bits.opcode
  instructionChecker.input.function3bits := io.reservationStation.bits.function3
  instructionChecker.input.function7bits := io.reservationStation.bits.immediateOrFunction7

  val destinationRegister = Wire(UInt(64.W))
  val immediateOrFunction7Extended = Mux(io.reservationStation.bits.immediateOrFunction7(11), (!0.U(64.W)) & io.reservationStation.bits.immediateOrFunction7, 0.U(64.W) | io.reservationStation.bits.immediateOrFunction7)
  val branchedProgramCounter = io.reservationStation.bits.programCounter + (immediateOrFunction7Extended ## 0.U(1.W)).asSInt
  val nextProgramCounter = io.reservationStation.bits.programCounter + 4.S

  // set destinationRegister
  io.reservationStation.ready := true.B
  when(io.reservationStation.valid) {
    destinationRegister := MuxCase(0.U, Seq(
      // 加算
      (instructionChecker.output.instruction === Instructions.Load ||
        (instructionChecker.output.arithmetic === ArithmeticOperations.Addition))
        -> (io.reservationStation.bits.value1 + io.reservationStation.bits.value2),
      // 減算
      (instructionChecker.output.arithmetic === ArithmeticOperations.Subtract)
        -> (io.reservationStation.bits.value1 - io.reservationStation.bits.value2),
      // 論理積
      (instructionChecker.output.arithmetic === ArithmeticOperations.And)
        -> (io.reservationStation.bits.value1 & io.reservationStation.bits.value2),
      // 論理和
      (instructionChecker.output.arithmetic === ArithmeticOperations.Or)
        -> (io.reservationStation.bits.value1 | io.reservationStation.bits.value2),
      // 排他的論理和
      (instructionChecker.output.arithmetic === ArithmeticOperations.Xor)
        -> (io.reservationStation.bits.value1 ^ io.reservationStation.bits.value2),
      // 左シフト
      (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftLeftLogical)
        -> (io.reservationStation.bits.value1 << io.reservationStation.bits.value2(5, 0)),
      // 右シフト(論理)
      (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftRightLogical)
        -> (io.reservationStation.bits.value1 >> io.reservationStation.bits.value2(5, 0)),
      //右シフト(算術)
      (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftRightArithmetic)
        -> (io.reservationStation.bits.value1.asSInt >> io.reservationStation.bits.value2(5, 0)).asUInt,
      // 比較(格納先：rd)(符号付き)
      (instructionChecker.output.arithmetic === ArithmeticOperations.SetLessThan)
        -> (io.reservationStation.bits.value1.asSInt < io.reservationStation.bits.value2.asSInt).asUInt,
      // 比較(格納先：rd)(符号なし)
      (instructionChecker.output.arithmetic === ArithmeticOperations.SetLessThanUnsigned)
        -> (io.reservationStation.bits.value1 < io.reservationStation.bits.value2),
      // 無条件ジャンプ
      (instructionChecker.output.instruction === Instructions.jal || instructionChecker.output.instruction === Instructions.jalr)
        -> (io.reservationStation.bits.programCounter.asUInt + 4.U),
      // lui
      (instructionChecker.output.instruction === Instructions.lui)
        -> io.reservationStation.bits.value2,
      // auipc
      (instructionChecker.output.instruction === Instructions.auipc)
        -> (io.reservationStation.bits.value2 + io.reservationStation.bits.programCounter.asUInt),
      // 分岐((
      // Equal
      (instructionChecker.output.branch === BranchOperations.Equal)
        -> (io.reservationStation.bits.value1 === io.reservationStation.bits.value2),
      // NotEqual
      (instructionChecker.output.branch === BranchOperations.NotEqual)
        -> (io.reservationStation.bits.value1 =/= io.reservationStation.bits.value2),
      // Less Than (signed)
      (instructionChecker.output.branch === BranchOperations.LessThan)
        -> (io.reservationStation.bits.value1.asSInt < io.reservationStation.bits.value2.asSInt),
      // Less Than (unsigned)
      (instructionChecker.output.branch === BranchOperations.LessThanUnsigned)
        -> (io.reservationStation.bits.value1 < io.reservationStation.bits.value2),
      // Greater Than (signed)
      (instructionChecker.output.branch === BranchOperations.GreaterOrEqual)
        -> (io.reservationStation.bits.value1.asSInt >= io.reservationStation.bits.value2.asSInt),
      // Greater Than (unsigned)
      (instructionChecker.output.branch === BranchOperations.GreaterOrEqualUnsigned)
        -> (io.reservationStation.bits.value1 >= io.reservationStation.bits.value2)
    ))

    io.fetch.valid := (instructionChecker.output.instruction === Instructions.Branch) || (instructionChecker.output.instruction === Instructions.jal) ||
      (instructionChecker.output.instruction === Instructions.auipc) || (instructionChecker.output.instruction === Instructions.jalr)
    when(io.fetch.valid) {
      io.fetch.programCounter := MuxCase(io.reservationStation.bits.programCounter, Seq(
        // 分岐
        // Equal
        (instructionChecker.output.branch === BranchOperations.Equal)
          -> Mux(io.reservationStation.bits.value1 === io.reservationStation.bits.value2,
          branchedProgramCounter, nextProgramCounter),
        // NotEqual
        (instructionChecker.output.branch === BranchOperations.NotEqual)
          -> Mux(io.reservationStation.bits.value1 =/= io.reservationStation.bits.value2,
          branchedProgramCounter, nextProgramCounter),
        // Less Than (signed)
        (instructionChecker.output.branch === BranchOperations.LessThan)
          -> Mux(io.reservationStation.bits.value1.asSInt < io.reservationStation.bits.value2.asSInt,
          branchedProgramCounter, nextProgramCounter),
        // Less Than (unsigned)
        (instructionChecker.output.branch === BranchOperations.LessThanUnsigned)
          -> Mux(io.reservationStation.bits.value1 < io.reservationStation.bits.value2,
          branchedProgramCounter, nextProgramCounter),
        // Greater Than (signed)
        (instructionChecker.output.branch === BranchOperations.GreaterOrEqual)
          -> Mux(io.reservationStation.bits.value1.asSInt >= io.reservationStation.bits.value2.asSInt,
          branchedProgramCounter, nextProgramCounter),
        // Greater Than (unsigned)
        (instructionChecker.output.branch === BranchOperations.GreaterOrEqualUnsigned)
          -> Mux(io.reservationStation.bits.value1 >= io.reservationStation.bits.value2,
          branchedProgramCounter, nextProgramCounter),
        // jal or auipc
        (instructionChecker.output.instruction === Instructions.auipc || instructionChecker.output.instruction === Instructions.jal)
          -> (io.reservationStation.bits.programCounter + io.reservationStation.bits.value2.asSInt),
        // jalr
        (instructionChecker.output.instruction === Instructions.jalr)
          -> Cat((io.reservationStation.bits.value1 + io.reservationStation.bits.value2) (63, 1), 0.U).asSInt
      ))
    }.otherwise {
      io.fetch.programCounter := nextProgramCounter
    }

  }.otherwise {
    destinationRegister := 0.U
    io.fetch.programCounter := nextProgramCounter
    io.fetch.valid := false.B
  }

  /**
   * 実行結果をリオーダバッファ,デコーダに送信
   * (validで送信データを調節)
   * (レジスタ挿入の可能性あり)
   */

  // LSQ
  io.loadStoreQueue.valid :=
    instructionChecker.output.instruction =/= Instructions.Unknown && io.reservationStation.valid
  io.loadStoreQueue.destinationTag := io.reservationStation.bits.destinationTag
  io.loadStoreQueue.value := Mux(instructionChecker.output.instruction === Instructions.Store,
    io.reservationStation.bits.value1 + immediateOrFunction7Extended, Mux(instructionChecker.output.operationWidth === OperationWidth.Word,
      Mux(destinationRegister(31), Cat(~0.U(32.W), destinationRegister(31, 0)), Cat(0.U(32.W), destinationRegister(31, 0))),
      destinationRegister))

  // reorder Buffer
  io.out.valid := io.reservationStation.valid &&
    instructionChecker.output.instruction =/= Instructions.Unknown &&
    instructionChecker.output.instruction =/= Instructions.Load // load命令の場合, ReorderBufferのvalueはDataMemoryから
  io.out.destinationTag := io.reservationStation.bits.destinationTag
  io.out.value := Mux(instructionChecker.output.operationWidth === OperationWidth.Word,
    Mux(destinationRegister(31), Cat(~0.U(32.W), destinationRegister(31, 0)), Cat(0.U(32.W), destinationRegister(31, 0))),
    destinationRegister)
}

object ExecutorElaborate extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new Executor(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}

