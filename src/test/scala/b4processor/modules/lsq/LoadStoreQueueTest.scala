package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.utils.{DecodeEnqueue, LSQfromALU}
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LoadStoreQueueWrapper(implicit params: Parameters) extends LoadStoreQueue {

  def setMemoryReady(value: Boolean): Unit = {
    for(mem <- this.io.memory) {
      mem.ready.poke(value)
    }
  }

  def SetFromExecutor(values: Seq[Option[LSQfromALU]] = Seq.fill(params.numberOfALUs)(None)): Unit = {
    for(i <- 0 until params.numberOfALUs) {
      val alu = this.io.alus(i)
      val value = values(i)
      alu.valid.poke(value.get.valid)
      alu.value.poke(value.get.value)
      alu.destinationTag.poke(value.get.destinationtag)
      alu.ProgramCounter.poke(value.get.ProgramCounter)
    }
  }

  def SetDecoder(values: Seq[DecodeEnqueue] = Seq.fill(params.numberOfDecoders)(DecodeEnqueue())): Unit = {
    for(i <- 0 until params.numberOfDecoders) {
      val decode = this.io.decoders(i)
      val value = values(i)
      decode.valid.poke(value.valid)
      decode.bits.stag2
    }
  }
}

class LoadStoreQueueTest {

}
