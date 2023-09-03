package b4processor.modules

import b4processor.Parameters
import b4processor.connections.{
  CollectedOutput,
  LoadStoreQueue2ReorderBuffer,
  OutputValue,
}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import b4processor.modules.memory.{
  MemoryReadTransaction,
  MemoryWriteTransaction,
  MemoryWriteIntent,
}
import b4processor.structures.memoryAccess.MemoryAccessWidth
import b4processor.utils.{B4RRArbiter, FIFO, Tag}
import b4processor.utils.operations.{
  AMOOperation,
  AMOOperationWidth,
  AMOOrdering,
}

import scala.math.pow

class Decoder2AtomicLSU(implicit params: Parameters) extends Bundle {
  val valid = Bool()
  val operation = AMOOperation.Type()
  val operationWidth = AMOOperationWidth.Type()
  val ordering = new AMOOrdering

  val destinationTag = new Tag

  val addressReg = new Tag
  val addressValue = UInt(64.W)
  val addressValid = Bool()

  val srcReg = new Tag
  val srcValue = UInt(64.W)
  val srcValid = Bool()

  val storeOk = Bool()
}

class AtomicLSU(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val decoders = Vec(
      params.threads,
      Vec(params.decoderPerThread, Flipped(Decoupled(new Decoder2AtomicLSU))),
    )
    val collectedOutput = Flipped(Vec(params.threads, new CollectedOutput()))
    val output = Decoupled(new OutputValue)
    val readRequest = Decoupled(new MemoryReadTransaction)
    val writeRequest = Decoupled(new MemoryWriteTransaction)
    val readResponse = Flipped(Decoupled(new OutputValue))
    val writeResponse = Flipped(Decoupled(new OutputValue))
    val reorderBuffer = Flipped(
      Vec(
        params.threads,
        Vec(
          params.maxRegisterFileCommitCount,
          Valid(new LoadStoreQueue2ReorderBuffer),
        ),
      ),
    )
  })

  io.output.valid := false.B
  io.output.bits := 0.U.asTypeOf(new OutputValue())
  io.readRequest.valid := false.B
  io.readRequest.bits := 0.U.asTypeOf(new MemoryReadTransaction())
  io.writeRequest.valid := false.B
  io.writeRequest.bits := 0.U.asTypeOf(new MemoryWriteTransaction())
  io.readResponse.ready := false.B
  io.writeResponse.ready := false.B
  io.writeRequest.bits.accessType := MemoryWriteIntent.Amo

  private val bufferLength = pow(2, 3).toInt
  private val heads = RegInit(
    VecInit(Seq.fill(params.threads)(0.U(log2Up(bufferLength).W))),
  )
  private val tails = RegInit(
    VecInit(Seq.fill(params.threads)(0.U(log2Up(bufferLength).W))),
  )
  private val buffer = Reg(
    Vec(params.threads, Vec(bufferLength, new Decoder2AtomicLSU)),
  )

  private val reservation = Reg(Vec(params.threads, Valid(UInt(64.W))))

  for (t <- 0 until params.threads) {
    val buf = buffer(t)
    for (o <- io.collectedOutput(t).outputs) {
      when(o.valid) {
        for (b <- buf) {
          when(b.addressReg === o.bits.tag && !b.addressValid && b.valid) {
            b.addressValue := o.bits.value
            b.addressValid := true.B
          }

          when(b.srcReg === o.bits.tag && !b.srcValid && b.valid) {
            b.srcValue := o.bits.value
            b.srcValid := true.B
          }
        }
      }
    }
  }
  for (t <- 0 until params.threads) {
    val rb = io.reorderBuffer(t)
    val tbuf = buffer(t)
    for (r <- rb) {
      when(r.valid) {
        for (b <- tbuf) {
          when(b.destinationTag === r.bits.destinationTag) {
            b.storeOk := true.B
          }
        }
      }
    }
  }

  for (t <- 0 until params.threads) {
    var nextHead = heads(t)
    for (i <- 0 until params.decoderPerThread) {
      val dec = io.decoders(t)(i)
      dec.ready := false.B
      when(nextHead + 1.U =/= tails(t)) {
        dec.ready := true.B
        when(dec.valid) {
          buffer(t)(nextHead) := dec.bits
        }
      }
      nextHead =
        Mux(nextHead + 1.U =/= tails(t) && dec.valid, nextHead + 1.U, nextHead)
    }
    heads(t) := nextHead
  }

  class AMOIssue extends Bundle {
    val operation = AMOOperation.Type()
    val operationWidth = AMOOperationWidth.Type()
    val ordering = new AMOOrdering
    val destinationTag = new Tag
    val addressValue = UInt(64.W)
    val srcValue = UInt(64.W)
  }

  val issueArbiter = Module(new B4RRArbiter(new AMOIssue, params.threads))
  val issueQueue = Module(new FIFO(3)(new AMOIssue))
  issueQueue.output.ready := false.B

  for (t <- 0 until params.threads) {
    val buf = buffer(t)(tails(t))
    val arb = issueArbiter.io.in(t)
    arb.valid := false.B
    arb.bits := 0.U.asTypeOf(new AMOIssue)

    when(
      heads(t) =/= tails(t) && buf.srcValid && buf.addressValid && buf.storeOk,
    ) {
      arb.valid := true.B
      arb.bits.operation := buf.operation
      arb.bits.ordering := buf.ordering
      arb.bits.operationWidth := buf.operationWidth
      arb.bits.destinationTag := buf.destinationTag
      arb.bits.addressValue := buf.addressValue
      arb.bits.srcValue := buf.srcValue
      when(arb.ready) {
        buf := 0.U.asTypeOf(new Decoder2AtomicLSU())
        tails(t) := tails(t) + 1.U
      }
    }
  }

  issueQueue.input <> issueArbiter.io.out

  private val load_request :: load_wait_response :: write_request :: write_wait_response :: write_back :: Nil =
    Enum(5)
  private val state = RegInit(load_request)
  val response = Reg(UInt(64.W))
  val isError = Reg(Bool())
  when(state === load_request) {
    isError := false.B
    response := 0.U
    when(!issueQueue.empty) {
      when(issueQueue.output.bits.operation === AMOOperation.Sc) {
        state := write_request
      }.otherwise {
        io.readRequest.valid := true.B
        val accessWidth = Mux(
          issueQueue.output.bits.operationWidth === AMOOperationWidth.Word,
          MemoryAccessWidth.Word,
          MemoryAccessWidth.DoubleWord,
        )
        io.readRequest.bits := MemoryReadTransaction.ReadToAmo(
          issueQueue.output.bits.addressValue,
          accessWidth,
          issueQueue.output.bits.destinationTag,
        )
        when(io.readRequest.ready) {
          state := load_wait_response
        }
      }
    }
  }

  when(state === load_wait_response) {
    io.readResponse.ready := true.B
    when(io.readResponse.valid) {
      assert(issueQueue.output.bits.destinationTag === io.readResponse.bits.tag)
      response := io.readResponse.bits.value
      isError := io.readResponse.bits.isError
      when(io.readResponse.bits.isError) {
        state := write_back
      }.elsewhen(issueQueue.output.bits.operation === AMOOperation.Lr) {
        state := write_back
        val res = reservation(issueQueue.output.bits.destinationTag.threadId)
        res.valid := true.B
        res.bits := issueQueue.output.bits.addressValue
      }.otherwise {
        state := write_request
      }
    }
  }

  when(state === write_request) {
    val res = reservation(issueQueue.output.bits.destinationTag.threadId)
    val scInvalid =
      issueQueue.output.bits.operation === AMOOperation.Sc && (!res.valid || res.bits =/= issueQueue.output.bits.addressValue)
    when(scInvalid) {
      response := 1.U
      state := write_back
      res.valid := false.B
    }.otherwise {
      when(issueQueue.output.bits.operation === AMOOperation.Sc) {
        response := 0.U
      }
      io.writeRequest.valid := true.B
      val srcValue = issueQueue.output.bits.srcValue
      import AMOOperation._
      val writeData = MuxLookup(issueQueue.output.bits.operation, 0.U)(
        Seq(
          Add -> (response + srcValue),
          And -> (response & srcValue),
          Max -> Mux(
            issueQueue.output.bits.operationWidth === AMOOperationWidth.DoubleWord,
            (response.asSInt.max(srcValue.asSInt)).asUInt,
            (response(31, 0).asSInt.max(srcValue(31, 0).asSInt)).asUInt,
          ),
          MaxU -> Mux(
            issueQueue.output.bits.operationWidth === AMOOperationWidth.DoubleWord,
            (response.max(srcValue)),
            (response(31, 0).max(srcValue(31, 0))),
          ),
          Min -> Mux(
            issueQueue.output.bits.operationWidth === AMOOperationWidth.DoubleWord,
            (response.asSInt.min(srcValue.asSInt)).asUInt,
            (response(31, 0).asSInt.min(srcValue(31, 0).asSInt)).asUInt,
          ),
          MinU -> Mux(
            issueQueue.output.bits.operationWidth === AMOOperationWidth.DoubleWord,
            (response.min(srcValue)),
            (response(31, 0).min(srcValue(31, 0))),
          ),
          Or -> (response | srcValue),
          Swap -> srcValue,
          Xor -> (response ^ srcValue),
          Sc -> srcValue,
        ),
      )
      io.writeRequest.bits.address := issueQueue.output.bits.addressValue
      io.writeRequest.bits.outputTag := issueQueue.output.bits.destinationTag
      io.writeRequest.bits.mask := Mux(
        issueQueue.output.bits.operationWidth === AMOOperationWidth.DoubleWord,
        0xff.U,
        Mux(issueQueue.output.bits.addressValue(2), 0xf0.U, 0x0f.U),
      )
      io.writeRequest.bits.data := Mux(
        issueQueue.output.bits.operationWidth === AMOOperationWidth.DoubleWord,
        writeData,
        Mux(
          issueQueue.output.bits.addressValue(2),
          writeData(31, 0) ## 0.U(32.W),
          writeData,
        ),
      )
      when(io.writeRequest.ready) {
        state := write_wait_response
        res.valid := false.B
        for (r <- reservation) {
          when(r.bits === issueQueue.output.bits.addressValue) {
            r.valid := false.B
          }
        }
      }
    }
  }

  when(state === write_wait_response) {
    io.writeResponse.ready := true.B
    when(io.writeResponse.valid) {
      state := write_back
      isError := isError | io.writeResponse.bits.isError
    }
  }

  when(state === write_back) {
    io.output.valid := true.B
    io.output.bits.value := response
    io.output.bits.isError := isError
    io.output.bits.tag := issueQueue.output.bits.destinationTag
    when(io.output.ready) {
      state := load_request
      issueQueue.output.ready := true.B
    }
  }
}

object AtomicLSU extends App {
  implicit val params = Parameters()
  ChiselStage.emitSystemVerilogFile(new AtomicLSU())
}
