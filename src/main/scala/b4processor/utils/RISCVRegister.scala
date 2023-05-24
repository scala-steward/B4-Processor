package b4processor.utils

import chisel3._
import circt.stage.ChiselStage

class RISCVRegister extends Bundle {
  val inner = UInt(5.W)

  override def toPrintable: Printable = {
      Printables(List(PString("x"), this.inner.toPrintable, PString("/hello")))
  }
  def toUInt: UInt = this.inner
}

object RISCVRegister {
  val regNameList = List("zero", "")

  def apply(n: Int): RISCVRegister = {
    RISCVRegister(n.U)
  }

  def apply(n: UInt): RISCVRegister = {
    val w = Wire(new RISCVRegister)
    w.inner := n
    w
  }
}

class SomeModule extends Module {
  val r = RISCVRegister(5)
  printf(p"$r")
}

object SomeModule extends App {
  ChiselStage.emitSystemVerilogFile(new SomeModule)
}
