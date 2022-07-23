package b4processor

import b4processor.utils.InstructionUtil
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorRISCVTestWrapper(instructions: String)(implicit
  params: Parameters
) extends B4ProcessorWithMemory(instructions) {
  def riscv_test(): Unit = {
    while (this.io.registerFileContents.get(16).peekInt() != 93) {
      this.clock.step()
    }
    val reg3_value = this.io.registerFileContents.get(2).peekInt()
    val fail_num = reg3_value >> 1
    this.io.registerFileContents.get(2).expect(1, s"failed on test ${fail_num}")
  }
}

class B4ProcessorRISCVTest extends AnyFlatSpec with ChiselScalatestTester {
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams =
    Parameters(
      debug = true,
      runParallel = 1,
      tagWidth = 4,
      loadStoreQueueIndexWidth = 2,
      maxRegisterFileCommitCount = 2
    )

  behavior of s"RISC-V tests rv64i"

  def riscv_test(test_name: String, timeout: Int = 1000): Unit = {

    it should s"run risc-v test ${test_name}" in {
      test( // FIXME fromFile8bit
        new B4ProcessorRISCVTestWrapper(
          s"riscv-tests-files/rv64ui-p-${test_name}"
        )
      )
        .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
          c =>
            c.clock.setTimeout(timeout)
            c.riscv_test()
        }
    }
  }

  riscv_test("add")
  riscv_test("addi")
  riscv_test("addiw")
  riscv_test("addw")
  riscv_test("and")
  riscv_test("andi")
  riscv_test("auipc")
  riscv_test("beq")
  riscv_test("bge")
  riscv_test("bgeu")
  riscv_test("blt")
  riscv_test("bltu")
  riscv_test("bne")
  // Fenceのテストは命令メモリとデータメモリが同じ空間にあることを前提にしていて、データメモリ上に命令を書き出してジャンプするので、今回はテストできない。
  riscv_test("fence_i")
  riscv_test("jal")
  riscv_test("jalr")
  riscv_test("lb")
  riscv_test("lbu")
  riscv_test("ld")
  riscv_test("lh")
  riscv_test("lhu")
  riscv_test("lui")
  riscv_test("lw")
  riscv_test("lwu")
  riscv_test("or")
  riscv_test("ori")
  riscv_test("sb")
  riscv_test("sd", timeout = 2000)
  riscv_test("sh")
  riscv_test("sll")
  riscv_test("slli")
  riscv_test("slliw")
  riscv_test("sllw")
  riscv_test("slt")
  riscv_test("slti")
  riscv_test("sltiu")
  riscv_test("sltu")
  riscv_test("sra")
  riscv_test("srai")
  riscv_test("sraiw")
  riscv_test("sraw")
  riscv_test("srl")
  riscv_test("srli")
  riscv_test("srliw")
  riscv_test("srlw")
  riscv_test("sub")
  riscv_test("subw")
  riscv_test("sw")
  riscv_test("xor")
  riscv_test("xori")
}
