package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.utils.ExecutorValue
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LoadStoreQueueWrapper(implicit params: Parameters) extends LoadStoreQueue {
  def initialize(): Unit = {

  }

  def setMemoryReady(value: Boolean): Unit = {
    for (mem <- this.io.memory) {
      mem.ready.poke(value)
    }
  }

  def expectExecutor(values: Seq[Option[ExecutorValue]]): Unit = {
    for ((alu, v) <- this.io.executors.zip(values)) {
      //      alu.valid.poke(v.isDefined)
      //      alu.value.poke()
    }
  }
}

class LoadStoreQueueTest {

}
