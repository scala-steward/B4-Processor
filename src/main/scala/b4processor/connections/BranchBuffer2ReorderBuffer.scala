package b4processor.connections

import chisel3._
import chisel3.util._

class BranchBuffer2ReorderBuffer
  extends Valid(new Bundle {
    val BranchID = UInt(3.W)
    val correct = Bool()
  })
