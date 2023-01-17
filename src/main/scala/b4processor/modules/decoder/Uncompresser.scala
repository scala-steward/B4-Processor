package b4processor.modules.decoder

import b4processor.connections.FetchBuffer2Decoder
import chisel3._
import chisel3.util._

object CExtension {
  def ADDI4SPN: BitPat = BitPat("b000")
}

object FormatI {
  def apply(operation: (UInt, UInt), rd: UInt, rs1: UInt, imm: UInt): UInt = {
    val output = Wire(UInt(64.W))
    output := imm ## rs1 ## operation._2 ## rd ## operation._1
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
    offset(12) ## offset(10, 5) ## src2 ##
      src1 ## operation._2 ## offset(4, 1) ##
      offset(11) ## operation._1
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
      "b00".U -> 0.U,
      "b01".U -> MuxCase(
        0.U,
        Seq(
          (instruction(15, 13) === "b010".U && instruction(11, 7) =/= 0.U) ->
            FormatI(
              FormatI.addi,
              instruction(11, 7),
              0.U(5.W),
              instruction(12) ## instruction(6, 2)
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
          )
        )
      ),
      "b11".U -> instruction
    )
  )
}
