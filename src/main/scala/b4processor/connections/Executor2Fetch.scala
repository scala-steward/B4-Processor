package b4processor.connections

import chisel3._
import chisel3.experimental.BundleLiterals._

class Executor2Fetch extends Bundle {
  val valid = Bool()
  val programCounter = SInt(64.W)
}

object Executor2Fetch {
  def noResult(): Executor2Fetch = (new Executor2Fetch).Lit(_.valid -> false.B, _.programCounter -> 0.S)

  def branch(address: SInt): Executor2Fetch = (new Executor2Fetch).Lit(_.valid -> true.B, _.programCounter -> address)
}