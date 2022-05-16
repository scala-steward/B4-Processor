package b4processor

import chiseltest._
import chisel3._
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  implicit val defaultParams = Parameters()

  it should "compile" in {
    test(new B4Processor) { c =>
    }
  }
}
