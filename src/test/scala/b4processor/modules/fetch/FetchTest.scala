package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.Fetch2BranchPrediction
import b4processor.modules.cache.InstructionMemoryCache
import b4processor.modules.memory.InstructionMemory
import b4processor.utils.InstructionUtil
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FetchWrapper(memoryInit: => Seq[UInt])(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val prediction = Vec(params.numberOfDecoders, new Fetch2BranchPrediction)
    val memoryAddress = Output(SInt(64.W))
    val memoryOutput = Vec(params.fetchWidth, Output(UInt(64.W)))
    val cacheAddress = Vec(params.numberOfDecoders, Output(SInt(64.W)))
    val cacheOutput = Vec(params.numberOfDecoders, Valid(UInt(64.W)))
    val PC = Output(SInt(64.W))
    val nextPC = Output(SInt(64.W))
    val isPrediction = Output(Bool())
    val nextIsPrediction = Output(Bool())
  })

  val fetch = Module(new Fetch)
  val cache = Module(new InstructionMemoryCache)
  val memory = Module(new InstructionMemory(memoryInit))

  fetch.io.prediction <> io.prediction
  cache.io.fetch <> fetch.io.cache
  cache.io.memory <> memory.io

  io.memoryOutput <> memory.io.output
  io.memoryAddress <> cache.io.memory.address
  fetch.io.cache.zip(io.cacheAddress).foreach { case (f, c) => f.address <> c }
  cache.io.fetch.zip(io.cacheOutput).foreach { case (c, f) => c.output <> f }

  io.PC := fetch.io.PC.get
  io.nextPC := fetch.io.nextPC.get
  io.isPrediction := fetch.io.isPrediction.get
  io.nextIsPrediction := fetch.io.nextIsPrediction.get

  fetch.io.decoders.foreach(_.ready := true.B)

  def initialize(): Unit = {
    this.setPrediction(Seq.fill(params.numberOfDecoders)(false))
  }

  def setPrediction(values: Seq[Boolean]): Unit = {
    for (i <- 0 until params.numberOfDecoders) {
      this.io.prediction(i).prediction.poke(values(i))
    }
  }

  def expectMemory(values: Seq[UInt]): Unit = {
    this.io.memoryOutput.zip(values).foreach { case (out, v) => out.expect(v) }
  }
}

class FetchTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Fetch"
  implicit val defaultParams = Parameters(debug = true)

  // 普通の命令と分岐を区別できるか
  // 書き込む命令
  // nop                  00000013
  // beq zero,zero,LABEL  00000463
  // nop                  00000013
  // LABEL:
  it should "load memory" in {
    test(new FetchWrapper(InstructionUtil.fromStringSeq32bit(Seq("00000013", "00000463", "00000013")))) { c =>
      c.initialize()

      c.io.memoryAddress.expect(0)
      c.expectMemory(Seq("x00000013".U, "x00000463".U))
      c.io.cacheAddress(0).expect(0)
      c.io.cacheAddress(1).expect(4)

      c.io.cacheOutput(0).bits.expect("x00000013".U)
      c.io.cacheOutput(0).valid.expect(true)
      c.io.prediction(0).isBranch.expect(false)

      c.io.cacheOutput(1).bits.expect("x00000463".U)
      c.io.cacheOutput(1).valid.expect(true)
      c.io.prediction(1).isBranch.expect(true)
    }
  }

  // 予測で値がどちらとも読み取れる
  // 書き込む命令
  // LOOP:
  // beq zero,zero,LOOP 00000063
  it should "read both values in loop" in {
    test(new FetchWrapper(InstructionUtil.fromStringSeq32bit(Seq("00000063", "00000000")))) { c =>
      c.initialize()
      c.setPrediction(Seq(true, true))

      c.io.memoryAddress.expect(0)
      c.expectMemory(Seq("x00000063".U, "x00000000".U))
      c.io.cacheAddress(0).expect(0)
      c.io.cacheAddress(0).expect(0)

      c.io.cacheOutput(0).bits.expect("x00000063".U)
      c.io.cacheOutput(0).valid.expect(true)
      c.io.prediction(0).isBranch.expect(true)

      c.io.cacheOutput(1).bits.expect("x00000063".U)
      c.io.cacheOutput(1).valid.expect(true)
      c.io.prediction(1).isBranch.expect(true)
    }
  }

  // 普通の命令と分岐先を認識できているか
  // 書き込む命令
  // nop                  00000013
  // beq zero,zero,LABEL  00000463
  // nop                  00000013
  // LABEL:
  it should "understand branch prediction=false" in {
    test(new FetchWrapper(InstructionUtil.fromStringSeq32bit(Seq("00000013", "00000463", "00000013")))) { c =>
      c.initialize()
      c.io.nextPC.expect(8)
      c.io.nextIsPrediction.expect(true)
    }
  }

  // 普通の命令と分岐先を認識できているか
  // 書き込む命令
  // nop                  00000013
  // beq zero,zero,LABEL  00000463
  // nop                  00000013
  // LABEL:
  it should "understand branch prediction=true" in {
    test(new FetchWrapper(InstructionUtil.fromStringSeq32bit(Seq("00000013", "00000463", "00000013")))) { c =>
      c.initialize()
      c.setPrediction(Seq(true, true))
      c.io.nextPC.expect(12)
      c.io.nextIsPrediction.expect(true)
    }
  }

  // ループで自身と同じアドレスに戻ってくるか
  // 書き込む命令
  // nop                00000013
  // LOOP:
  // beq zero,zero,LOOP 00000063
  it should "understand loop to self" in {
    test(new FetchWrapper(InstructionUtil.fromStringSeq32bit(Seq("00000013", "00000063")))) { c =>
      c.initialize()
      c.setPrediction(Seq(true, true))
      c.io.nextPC.expect(4)
      c.io.nextIsPrediction.expect(true)
    }
  }

  // ループで一つ前に戻る
  // 書き込む命令
  // LOOP:
  // nop                00000013
  // beq zero,zero,LOOP fe000ee3
  it should "understand loop" in {
    test(new FetchWrapper(InstructionUtil.fromStringSeq32bit(Seq("00000013", "fe000ee3")))) { c =>
      c.initialize()
      c.setPrediction(Seq(true, true))
      c.io.nextPC.expect(0)
      c.io.nextIsPrediction.expect(true)
    }
  }

  it should "fetch with prediction always false" in {
    test(new FetchWrapper(InstructionUtil.fromFile8bit("riscv-sample-programs/branch/branch.8.hex"))) { c =>
      c.initialize()
      c.setPrediction(Seq(false, false))

      c.io.memoryAddress.expect(0)
      c.expectMemory(Seq("x00200513".U, "x00300593".U))

      c.clock.step()

      c.io.memoryAddress.expect(8)
      c.expectMemory(Seq("x00a58633".U, "x00061663".U))

      c.clock.step()

      c.io.memoryAddress.expect(12)
      c.expectMemory(Seq("x00a58633".U, "x00061663".U))
    }
  }

  it should "fetch with prediction always true" in {
    test(new FetchWrapper(InstructionUtil.fromFile8bit("riscv-sample-programs/branch/branch.8.hex"))) { c =>
      c.initialize()
      c.setPrediction(Seq(true, true))

      c.io.memoryAddress.expect(0)
      c.expectMemory(Seq("x00200513".U, "x00300593".U))

      c.clock.step()

      c.io.memoryAddress.expect(8)
      c.expectMemory(Seq("x00a58633".U, "x00061663".U))

      c.clock.step()

      c.io.memoryAddress.expect(16)
      c.expectMemory(Seq("x00a58633".U, "x00061663".U))
    }
  }
}
