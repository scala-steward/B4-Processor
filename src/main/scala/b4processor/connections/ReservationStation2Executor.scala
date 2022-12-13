package b4processor.connections

import b4processor.Parameters
import b4processor.utils.Tag
import chisel3._
import chisel3.util._

class ReservationStation2Executor(implicit params: Parameters)
    extends ReadyValidIO(new Bundle {
      val destinationTag = new Tag
      val value1 = UInt(64.W)
      val value2 = UInt(64.W)
      val function3 = UInt(3.W)
      val immediateOrFunction7 = UInt(12.W)
      val opcode = UInt(7.W)
    })
