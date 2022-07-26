package b4processor.modules.branchprediction

import b4processor.Parameters
import b4processor.connections.{BranchBuffer2ReorderBuffer, Fetch2BranchBuffer}
import b4processor.modules.branch_output_collector.CollectedBranchAddresses
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

import scala.math.pow

class BranchBuffer(implicit params: Parameters) extends Module {
  val buffer_size = params.branchBufferSize

  val io = IO(new Bundle {
    val fetch = Flipped(new Fetch2BranchBuffer)
    val reorderBuffer = new BranchBuffer2ReorderBuffer
    val branchOutput = Flipped(new CollectedBranchAddresses)
  })

  val buffer = Reg(Vec(pow(2, buffer_size).toInt, new BranchBufferEntry))
  val head = RegInit(0.U(buffer_size.W))
  val tail = RegInit(0.U(buffer_size.W))

  val flush = RegInit(false.B)
  val flushUntil = RegInit(0.U(buffer_size.W))

  {
    var nextHead = head
    for (b <- io.fetch.branches) {
      val indexOk = nextHead + 1.U =/= tail
      b.ready := indexOk
      b.branchID := DontCare
      val valid = b.valid && indexOk
      when(valid) {
        buffer(nextHead) := BranchBufferEntry(b.address)
        b.branchID := nextHead
      }
      nextHead = Mux(valid, nextHead + 1.U, nextHead)
    }
    head := nextHead
  }

  {
    val r = io.reorderBuffer

    io.fetch.changeAddress.valid := false.B
    io.fetch.changeAddress.bits := DontCare
    r.valid := true.B
    r.bits := DontCare

    val indexOk = tail =/= head
    val entry = buffer(tail)
    when(indexOk && entry.correctValid) {
      r.valid := true.B
      r.bits.BranchID := tail
      r.bits.correct := entry.correct

      tail := tail + 1.U
      when(!entry.correct) {
        io.fetch.changeAddress.valid := true.B
        io.fetch.changeAddress.bits := entry.address
        flush := true.B
        flushUntil := head
      }
    }
  }

  for (bo <- io.branchOutput.addresses) {
    when(bo.valid) {
      val entry = buffer(bo.branchID)
      entry.correctValid := true.B
      entry.correct := entry.address === bo.address
      entry.address := bo.address
    }
  }
}

object BranchBuffer extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(
    new BranchBuffer(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}

class BranchBufferEntry extends Bundle {

  /** アドレス 予測された値を記録し、予測された値が後に誤っているとわかったら変更する */
  val address = SInt(64.W)

  /** ブランチが正しい */
  val correct = Bool()

  /** ブランチ結果が到達している */
  val correctValid = Bool()
}

object BranchBufferEntry {
  def apply(predictedAddress: SInt): BranchBufferEntry = {
    val w = Wire(new BranchBufferEntry)
    w.address := predictedAddress
    w.correct := DontCare
    w.correctValid := false.B
    w
  }
}
