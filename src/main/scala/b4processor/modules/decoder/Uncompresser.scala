package b4processor.modules.decoder

import b4processor.connections.FetchBuffer2Decoder
import chisel3._
import chisel3.util._

object CExtension {
  def ADDI4SPN: BitPat = BitPat("b000")
}

object FormatI {
  def apply(operation: (UInt, UInt), rd: UInt, rs1: UInt, imm: UInt): UInt = {
    require(operation._1.getWidth == 7)
    require(operation._2.getWidth == 3)
    require(rs1.getWidth == 5)
    require(rd.getWidth == 5)
    require(imm.getWidth == 12)
    val output = imm ## rs1 ## operation._2 ## rd ## operation._1
    require(output.getWidth == 32)
    output
  }
  def addi: (UInt, UInt) = ("b0010011".U(7.W), "b000".U(3.W))
  def ld: UInt = 0.U
}

object FormatB {
  def apply(
    operation: (UInt, UInt),
    src1: UInt,
    src2: UInt,
    offset: UInt
  ): UInt = {
    require(operation._1.getWidth == 7)
    require(operation._2.getWidth == 3)
    require(src1.getWidth == 5)
    require(src2.getWidth == 5)
    require(offset.getWidth == 13)
    val output = offset(12) ## offset(10, 5) ## src2 ##
      src1 ## operation._2 ## offset(4, 1) ##
      offset(11) ## operation._1
    require(output.getWidth == 32)
    output
  }

  def beq = ("b1100011".U(7.W), "b000".U(3.W))
  def bne = ("b1100011".U(7.W), "b001".U(3.W))
}

object FormatJ {
  def apply(opcode: UInt, rd: UInt, imm: UInt): UInt = {
    require(rd.getWidth == 5)
    require(opcode.getWidth == 7)
    require(imm.getWidth == 22)
    val output = Cat(imm(20), imm(10, 1), imm(11), imm(19, 12), rd, opcode)
    require(output.getWidth == 32)
    output
  }

  def jal = "b1101111".U(7.W)
}

object SignExtend {
  def apply(data: UInt, width: Int): UInt = {
    val w = Wire(UInt(width.W))
    w := Mux(
      !data(data.getWidth - 1),
      data,
      ~0.U((width - data.getWidth).W) ## data
    )
    w
  }
}

object ZeroExtend {
  def apply(data: UInt, width: Int): UInt = {
    val w = Wire(UInt(width.W))
    w := data
    w
  }
}

class Uncompresser extends Module {
  val io = IO(new Bundle {
    val fetch = Flipped(new FetchBuffer2Decoder())
    val decoder = new FetchBuffer2Decoder()
  })

  io.fetch.ready := io.decoder.ready
  io.decoder.valid := io.fetch.valid
  io.decoder.bits.programCounter := io.fetch.bits.programCounter

  val instruction = io.fetch.bits.instruction
  val shortinst = instruction(15, 0)

  io.decoder.bits.instruction := MuxLookup(
    instruction(1, 0),
    0.U,
    Seq(
      "b00".U -> MuxCase(
        0.U,
        Seq(
          (instruction(15, 13) === "b000".U && instruction(
            12,
            5
          ) =/= 0.U) -> FormatI(
            FormatI.addi,
            1.U(2.W) ## instruction(4, 2),
            2.U(5.W),
            ZeroExtend(
              Cat(
                instruction(10, 7),
                instruction(12, 11),
                instruction(5),
                instruction(6),
                0.U(2.W)
              ),
              12
            )
          )
        )
      ),
      "b01".U -> MuxCase(
        0.U,
        Seq(
          (instruction(15, 13) === "b000".U && instruction(11, 7) === 0.U) ->
            FormatI(FormatI.addi, 0.U(5.W), 0.U(5.W), 0.U(12.W)),
          (instruction(15, 13) === "b000".U && instruction(11, 7) =/= 0.U) ->
            FormatI(
              FormatI.addi,
              instruction(11, 7),
              instruction(11, 7),
              SignExtend(instruction(12) ## instruction(6, 2), 12)
            ),
          (instruction(15, 13) === "b010".U && instruction(11, 7) =/= 0.U) ->
            FormatI(
              FormatI.addi,
              instruction(11, 7),
              0.U(5.W),
              SignExtend(instruction(12) ## instruction(6, 2), 12)
            ),
          (instruction(15, 13) === "b110".U) -> FormatB(
            FormatB.beq,
            1.U(2.W) ## instruction(9, 7),
            0.U(5.W),
            SignExtend(
              Cat(
                instruction(12),
                instruction(6, 5),
                instruction(2),
                instruction(11, 10),
                instruction(4, 3),
                0.U(1.W)
              ),
              13
            )
          ),
          (instruction(15, 13) === "b111".U) -> FormatB(
            FormatB.bne,
            1.U(2.W) ## instruction(9, 7),
            0.U(5.W),
            SignExtend(
              Cat(
                instruction(12),
                instruction(6, 5),
                instruction(2),
                instruction(11, 10),
                instruction(4, 3),
                0.U(1.W)
              ),
              13
            )
          ),
          (instruction(15, 13) === "b101".U) -> FormatJ(
            FormatJ.jal,
            0.U(5.W),
            SignExtend(
              Cat(
                instruction(12),
                instruction(8),
                instruction(10, 9),
                instruction(6),
                instruction(7),
                instruction(2),
                instruction(11),
                instruction(5, 3),
                0.U(1.W)
              ),
              22
            )
          ),
          (instruction(15, 13) === "b011".U && instruction(
            11,
            7
          ) === 2.U) -> FormatI(
            FormatI.addi,
            2.U(5.W),
            2.U(5.W),
            SignExtend(
              Cat(
                instruction(12),
                instruction(4, 3),
                instruction(5),
                instruction(2),
                instruction(6),
                0.U(4.W)
              ),
              12
            )
          )
        )
      ),
      "b11".U -> instruction
    )
  )
}
