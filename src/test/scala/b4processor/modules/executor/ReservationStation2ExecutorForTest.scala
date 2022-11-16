package b4processor.modules.executor

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3.util.ReadyValidIO
import chisel3._

class ReservationStation2ExecutorForTest(implicit params: Parameters)
    extends ReadyValidIO(new Bundle {
      val destinationTag = new Tag
      val value1 = SInt(64.W)
      val value2 = SInt(64.W)
      val function3 = UInt(3.W)
      val immediateOrFunction7 = UInt(12.W)
      val opcode = UInt(7.W)
      val programCounter = UInt(64.W)
    })
