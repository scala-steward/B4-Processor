package b4processor.modules.memory

import b4processor.Parameters
import b4processor.utils.DataMemoryValue
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class DataMemoryTestWrapper(implicit params: Parameters) extends AnyFlatSpec with ChiselScalatestTester {
  def setMemory(values: Seq[Option[DataMemoryValue]] = Seq.fill(params.maxRegisterFileCommitCount)(None)): Unit = {

  }
}