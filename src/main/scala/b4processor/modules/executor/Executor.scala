package b4processor.modules.executor

import b4processor.Parameters
import b4processor.common.{
  ArithmeticOperations,
  BranchOperations,
  InstructionChecker,
  Instructions,
  OperationWidth
}
import b4processor.connections._
import b4processor.utils.Tag
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.{Mux, _}

class Executor(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reservationStation = Flipped(new ReservationStation2Executor)
    val out = Irrevocable(new OutputValue)
    //    val loadStoreQueue = Output(new Executor2LoadStoreQueue)
    val fetch = Irrevocable(new BranchOutput)
  })

  /** リザベーションステーションから実行ユニットへデータを送信 op,fuctionによって命令を判断し，計算を実行
    */
  io.reservationStation.ready := io.out.ready && io.fetch.ready
  val instructionChecker = Module(new InstructionChecker)

  instructionChecker.input.opcode := io.reservationStation.bits.opcode
  instructionChecker.input.function3bits := io.reservationStation.bits.function3
  instructionChecker.input.function7bits := io.reservationStation.bits.immediateOrFunction7

  val executionResult64bit = Wire(UInt(64.W))
  val immediateOrFunction7Extended =
    io.reservationStation.bits.immediateOrFunction7
  val branchedProgramCounter =
    (io.reservationStation.bits.programCounter.asSInt + (immediateOrFunction7Extended ## 0
      .U(1.W)).asSInt).asUInt
  val nextProgramCounter = io.reservationStation.bits.programCounter + 4.U

  io.fetch.bits.programCounter := 0.U
  io.fetch.valid := false.B
  io.fetch.bits.threadId := 0.U
  io.out.valid := false.B
  executionResult64bit := 0.U

  // set destinationRegister
  io.reservationStation.ready := true.B
  when(io.reservationStation.valid) {
    io.out.valid := true.B
    //    printf("pc=%x, immediate=%d\n", io.reservationStation.bits.programCounter, immediateOrFunction7Extended.asSInt)
    //    printf("a = %d b = %d\n", io.reservationStation.bits.value1, io.reservationStation.bits.value2)
    executionResult64bit := MuxCase(
      0.U,
      Seq(
        // ストア
        (instructionChecker.output.instruction === Instructions.Store)
          -> (io.reservationStation.bits.value1.asSInt + immediateOrFunction7Extended.asSInt).asUInt,
        // 加算
        (instructionChecker.output.instruction === Instructions.Load ||
          (instructionChecker.output.arithmetic === ArithmeticOperations.Addition))
          -> (io.reservationStation.bits.value1 + io.reservationStation.bits.value2),
        // 減算
        (instructionChecker.output.arithmetic === ArithmeticOperations.Subtraction)
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
          -> Mux(
            instructionChecker.output.operationWidth === OperationWidth.Word,
            io.reservationStation.bits
              .value1(31, 0) << io.reservationStation.bits.value2(4, 0),
            io.reservationStation.bits.value1 << io.reservationStation.bits
              .value2(5, 0)
          ),
        // 右シフト(論理)
        (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftRightLogical)
          -> Mux(
            instructionChecker.output.operationWidth === OperationWidth.Word,
            io.reservationStation.bits
              .value1(31, 0) >> io.reservationStation.bits.value2(4, 0),
            io.reservationStation.bits.value1 >> io.reservationStation.bits
              .value2(5, 0)
          ),
        // 右シフト(算術)
        (instructionChecker.output.arithmetic === ArithmeticOperations.ShiftRightArithmetic)
          -> Mux(
            instructionChecker.output.operationWidth === OperationWidth.Word,
            (io.reservationStation.bits
              .value1(31, 0)
              .asSInt >> io.reservationStation.bits.value2(4, 0)).asUInt,
            (io.reservationStation.bits.value1.asSInt >> io.reservationStation.bits
              .value2(5, 0)).asUInt
          ),
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
          -> (io.reservationStation.bits.value2.asSInt).asUInt,
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
      )
    )

    io.fetch.valid := (instructionChecker.output.instruction === Instructions.Branch) ||
      // FIXME 用途がわからないからコメントアウトしたけど必要かもしれない
      //      (instructionChecker.output.instruction === Instructions.jal) ||
      //      (instructionChecker.output.instruction === Instructions.auipc) ||
      (instructionChecker.output.instruction === Instructions.jalr)
    when(io.fetch.valid) {
      io.fetch.bits.threadId := io.reservationStation.bits.destinationTag.threadId
      io.fetch.bits.programCounter := MuxCase(
        io.reservationStation.bits.programCounter,
        Seq(
          // 分岐
          // Equal
          (instructionChecker.output.branch === BranchOperations.Equal)
            -> Mux(
              io.reservationStation.bits.value1 === io.reservationStation.bits.value2,
              branchedProgramCounter,
              nextProgramCounter
            ),
          // NotEqual
          (instructionChecker.output.branch === BranchOperations.NotEqual)
            -> Mux(
              io.reservationStation.bits.value1 =/= io.reservationStation.bits.value2,
              branchedProgramCounter,
              nextProgramCounter
            ),
          // Less Than (signed)
          (instructionChecker.output.branch === BranchOperations.LessThan)
            -> Mux(
              io.reservationStation.bits.value1.asSInt < io.reservationStation.bits.value2.asSInt,
              branchedProgramCounter,
              nextProgramCounter
            ),
          // Less Than (unsigned)
          (instructionChecker.output.branch === BranchOperations.LessThanUnsigned)
            -> Mux(
              io.reservationStation.bits.value1 < io.reservationStation.bits.value2,
              branchedProgramCounter,
              nextProgramCounter
            ),
          // Greater Than (signed)
          (instructionChecker.output.branch === BranchOperations.GreaterOrEqual)
            -> Mux(
              io.reservationStation.bits.value1.asSInt >= io.reservationStation.bits.value2.asSInt,
              branchedProgramCounter,
              nextProgramCounter
            ),
          // Greater Than (unsigned)
          (instructionChecker.output.branch === BranchOperations.GreaterOrEqualUnsigned)
            -> Mux(
              io.reservationStation.bits.value1 >= io.reservationStation.bits.value2,
              branchedProgramCounter,
              nextProgramCounter
            ),
          // jal or auipc
          (instructionChecker.output.instruction === Instructions.auipc || instructionChecker.output.instruction === Instructions.jal)
            -> (io.reservationStation.bits.programCounter.asSInt + io.reservationStation.bits.value2.asSInt).asUInt,
          // jalr
          (instructionChecker.output.instruction === Instructions.jalr)
            -> Cat(
              (io.reservationStation.bits.value1 + io.reservationStation.bits.value2)(
                63,
                1
              ),
              0.U
            ).asUInt
        )
      )
    }
  }

  val executionResultSized = Wire(UInt(64.W))
  executionResultSized := Mux(
    !(instructionChecker.output.instruction === Instructions.Load ||
      instructionChecker.output.instruction === Instructions.Store)
      && instructionChecker.output.operationWidth === OperationWidth.Word,
    executionResult64bit(31, 0).asSInt,
    executionResult64bit.asSInt
  ).asUInt

  //  printf("original %d\nsized %d\n", executionResult64bit, executionResultSized)

  /** 実行結果をリオーダバッファ,デコーダに送信 (validで送信データを調節) (レジスタ挿入の可能性あり)
    */

  // reorder Buffer
  //  printf(p"instruction type = ${instructionChecker.output.instruction.asUInt}\n")
  io.out.bits.resultType := Mux(
    io.reservationStation.valid &&
      instructionChecker.output.instruction =/= Instructions.Unknown &&
      instructionChecker.output.instruction =/= Instructions.Load && // load命令の場合, ReorderBufferのvalueはDataMemoryから
      instructionChecker.output.instruction =/= Instructions.Store,
    ResultType.Result,
    ResultType.LoadStoreAddress
  ) // Store命令の場合、リオーダバッファでエントリは無視される

  when(io.out.valid) {
    io.out.bits.tag := io.reservationStation.bits.destinationTag
    io.out.bits.value := executionResultSized
  }.otherwise {
    io.out.bits.tag := DontCare
    io.out.bits.value := 0.U
  }

  io.out.bits.isError := false.B
}

object Executor extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(
    new Executor(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
