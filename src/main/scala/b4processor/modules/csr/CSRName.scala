package b4processor.modules.csr

import chisel3._

object CSRName {
  def cycle: UInt = "xC00".U
  def time: UInt = "xC01".U
  def instret: UInt = "xC02".U
  def mvendorid: UInt = "xF11".U
  def marchid: UInt = "xF12".U
  def mimpid: UInt = "xF13".U
  def mhartid: UInt = "xF14".U
  def mstatus: UInt = "x300".U
  def misa: UInt = "x301".U
  def medeleg: UInt = "x302".U
  def mideleg: UInt = "x303".U
  def mie: UInt = "x304".U
  def mtvec: UInt = "x305".U
  def mcounteren: UInt = "x306".U
  def mscratch: UInt = "x340".U
  def mepc: UInt = "x341".U
  def mcause: UInt = "x342".U
  def mtval: UInt = "x343".U
  def mip: UInt = "x344".U
  def mcycle: UInt = "xB00".U
}
