package b4processor.modules.fetch

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util._

/** フェッチモジュール用命令の種類のチェック */
class CheckBranch extends Module {
  val io = IO(new Bundle {

    /** 命令 */
    val instruction = Input(UInt(32.W))

    /** 分岐の種類 */
    val branchType = Output(BranchType())

    /** 分岐後の命令のオフセット */
    val offset = Output(SInt(21.W))
  })

  // オペコードを取り出す
  val opcode = io.instruction(6, 0)

  // オフセットの抽出
  io.offset := MuxLookup(
    opcode,
    4.S,
    Seq(
      // jalr
      "b1100111".U -> Cat(io.instruction(31, 20), 0.U(1.W)).asSInt,
      // jal
      "b1101111".U -> Cat(
        io.instruction(31),
        io.instruction(19, 12),
        io.instruction(20),
        io.instruction(30, 21),
        0.U(1.W)
      ).asSInt,
      // branch
      "b1100011".U -> Cat(
        io.instruction(31),
        io.instruction(7),
        io.instruction(30, 25),
        io.instruction(11, 8),
        0.U(1.W)
      ).asSInt,
      // fence, fence.i
      "b0001111".U -> 4.S
    )
  )

  // 瓶木の種類の抽出
  io.branchType := MuxLookup(
    opcode,
    BranchType.None,
    Seq(
      // jalr
      "b1100111".U -> BranchType.JALR,
      // jal
      "b1101111".U -> BranchType.JAL,
      // B
      "b1100011".U -> BranchType.Branch,
      // fence, fence.i
      "b0001111".U -> Mux(
        io.instruction(12),
        BranchType.FenceI,
        BranchType.Fence
      )
    )
  )
}

object CheckBranch extends App {
  (new ChiselStage).emitVerilog(new CheckBranch)
}
