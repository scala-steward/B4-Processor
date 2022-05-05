package b4processor.utils

import chisel3._

import scala.io.Source

object InstructionUtil {
  def fromFile8bit(filename: String): Seq[UInt] = {
    val file = Source.fromFile(filename)
    val output = file.getLines().map(v => s"x$v".U(8.W)).toSeq
    file.close()
    output
  }

  def fromStringSeq32bit(seq: Seq[String]): Seq[UInt] = {
    var output = Seq[String]()
    for (s <- seq) {
      var offset = 0
      if (s.startsWith("x"))
        offset = 1
      for (i <- (0 until 4).reverse)
        output = output :+ s.slice(i * 2 + offset, i * 2 + 2 + offset)
    }
    output.map(n => s"x$n".U(8.W))
  }
}
