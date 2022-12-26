package b4processor.modules.csr

import chisel3._

object CSRName {
  def cycle: UInt = "xC00".U
  def time: UInt = "xC01".U
  def instret: UInt = "xC02".U
  def mhartid: UInt = "xF14".U
  def misa: UInt = "x301".U
}
