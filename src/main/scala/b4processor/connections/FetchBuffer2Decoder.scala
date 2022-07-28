package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util.ReadyValidIO

/** 命令とデコーダをつなぐ
  */
class FetchBuffer2Decoder(implicit params: Parameters)
    extends ReadyValidIO(new Bundle {
      val instruction = UInt(32.W)
      val programCounter = SInt(64.W)
      val branchID = UInt(params.branchBufferSize.W)
      val isBranch = Bool()
    })
