package b4processor.modules.fetch

import b4processor.Parameters
import b4processor.connections.{
  BranchOutput,
  Fetch2BranchPrediction,
  Fetch2FetchBuffer
}
import b4processor.modules.branch_output_collector.CollectedBranchAddresses
import b4processor.modules.branchprediction.{BranchBuffer, BranchBufferWrapper}
import b4processor.modules.cache.InstructionMemoryCache
import b4processor.modules.memory.InstructionMemory
import b4processor.utils.InstructionUtil
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/** フェッチのラッパー
  *
  * フェッチ、キャッシュ、命令メモリを含む
  */
class FetchWrapper(memoryInit: => Seq[UInt])(implicit params: Parameters)
    extends Module {
  val io = IO(new Bundle {

    /** 分岐予測 */
    val prediction = Vec(params.runParallel, new Fetch2BranchPrediction)

    /** 実行ユニットからの分岐先の値 */
    val collectedBranchAddresses = Flipped(new CollectedBranchAddresses)

    /** デコーダ */
    val decoders = new Fetch2FetchBuffer

    /** ロードストアキューのエントリが空か */
    val loadStoreQueueEmpty = Input(Bool())

    /** リオーダバッファのエントリが空か */
    val reorderBufferEmpty = Input(Bool())

    /** メモリに要求されているアドレス */
    val memoryAddress = Output(SInt(64.W))

    /** メモリの出力 */
    val memoryOutput = Vec(params.fetchWidth, Output(UInt(64.W)))

    /** キャッシュに要求されているアドレス */
    val cacheAddress = Vec(params.runParallel, Output(SInt(64.W)))

    /** キャッシュからの出力 */
    val cacheOutput = Vec(params.runParallel, Valid(UInt(64.W)))

    /** プログラムカウンタ */
    val PC = Output(SInt(64.W))
  })

  val fetch = Module(new Fetch)
  val cache = Module(new InstructionMemoryCache)
  val memory = Module(new InstructionMemory(memoryInit))
  val branchBuffer = Module(new BranchBufferWrapper)

  fetch.io.prediction <> io.prediction
  fetch.io.fetchBuffer <> io.decoders
  fetch.io.reorderBufferEmpty <> io.reorderBufferEmpty
  fetch.io.loadStoreQueueEmpty <> io.loadStoreQueueEmpty
  fetch.io.branchBuffer <> branchBuffer.io.fetch
  branchBuffer.io.branchOutput <> io.collectedBranchAddresses

  cache.io.fetch <> fetch.io.cache
  cache.io.memory <> memory.io

  io.memoryOutput <> memory.io.output
  io.memoryAddress <> cache.io.memory.address
  fetch.io.cache.zip(io.cacheAddress).foreach { case (f, c) => f.address <> c }
  cache.io.fetch.zip(io.cacheOutput).foreach { case (c, f) => c.output <> f }

  io.PC := fetch.io.PC.get

  fetch.io.fetchBuffer.decoder.foreach(v => v.ready := true.B)

  /** 初期化 */
  def initialize(): Unit = {
    this.setPrediction(Seq.fill(params.runParallel)(false))
  }

  /** 予測をセット */
  def setPrediction(values: Seq[Boolean]): Unit = {
    for (i <- 0 until params.runParallel) {
      this.io.prediction(i).prediction.poke(values(i))
    }
  }

  /** 分岐先をセット */
  def setExecutorBranchResult(
    results: Seq[Option[(BigInt, Int)]] = Seq.fill(params.runParallel)(None)
  ): Unit = {
    for ((e, r) <- io.collectedBranchAddresses.addresses.zip(results)) {
      e.valid.poke(r.isDefined)
      if (r.isDefined) {
        val ru = r.get
        e.address.poke(ru._1)
        e.branchID.poke(ru._2)
      }
    }
  }

  /** メモリからの出力内容を確認 */
  def expectMemory(values: Seq[UInt]): Unit = {
    this.io.memoryOutput.zip(values).foreach { case (out, v) => out.expect(v) }
  }
}

class FetchTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Fetch"
  implicit val defaultParams =
    Parameters().copy(
      debug = true,
      runParallel = 2,
      instructionStart = 0x10000000
    )

  // 普通の命令と分岐を区別できるか
  // 書き込む命令
  // nop                  00000013
  // beq zero,zero,LABEL  00000463
  // nop                  00000013
  // LABEL:
  it should "load memory" in {
    test(
      new FetchWrapper(
        InstructionUtil
          .fromStringSeq32bit(Seq("00000013", "00000463", "00000013"))
      )
    ) { c =>
      c.initialize()

      c.io.memoryAddress.expect(0x10000000)
      c.expectMemory(Seq("x00000013".U, "x00000463".U))
      c.io.cacheAddress(0).expect(0x10000000)
      c.io.cacheAddress(1).expect(0x10000004)

      c.io.cacheOutput(0).bits.expect("x00000013".U)
      c.io.cacheOutput(0).valid.expect(true)

      c.io.cacheOutput(1).bits.expect("x00000463".U)
      c.io.cacheOutput(1).valid.expect(true)
    }
  }

  // 予測で値がどちらとも読み取れる
  // 書き込む命令
  // LOOP:
  // beq zero,zero,LOOP 00000063
  it should "read both values in loop" in {
    test(
      new FetchWrapper(
        InstructionUtil.fromStringSeq32bit(Seq("00000063", "00000000"))
      )
    ) { c =>
      c.initialize()
      c.setPrediction(Seq(true, true))

      c.io.memoryAddress.expect(0x10000000)
      c.expectMemory(Seq("x00000063".U, "x00000000".U))
      c.io.cacheAddress(0).expect(0x10000000)
      c.io.cacheAddress(0).expect(0x10000000)

      c.io.cacheOutput(0).bits.expect("x00000063".U)
      c.io.cacheOutput(0).valid.expect(true)

      c.io.cacheOutput(1).bits.expect("x00000063".U)
      c.io.cacheOutput(1).valid.expect(true)

      c.clock.step()
    }
  }

  // 普通の命令と分岐先を認識できているか
  // 書き込む命令
  // nop                  00000013
  // beq zero,zero,LABEL  00000463
  // nop                  00000013
  // LABEL:
  it should "understand branch prediction=false" in {
    test(
      new FetchWrapper(
        InstructionUtil
          .fromStringSeq32bit(Seq("00000013", "00000463", "00000013"))
      )(defaultParams.copy(runParallel = 1))
    ) { c =>
      c.initialize()
      c.clock.step()
      c.io.PC.expect(0x10000004)
      c.clock.step()
      c.io.PC.expect(0x10000008)
    }
  }

  // 普通の命令と分岐先を認識できているか
  // 書き込む命令
  // nop                  00000013
  // beq zero,zero,LABEL  00000463
  // nop                  00000013
  // LABEL:
  it should "understand branch prediction=true" in {
    test(
      new FetchWrapper(
        InstructionUtil
          .fromStringSeq32bit(Seq("00000013", "00000463", "00000013"))
      )(defaultParams.copy(runParallel = 1))
    ) { c =>
      c.initialize()
      c.setPrediction(Seq(true))
      c.clock.step()
      c.io.PC.expect(0x10000004)
      c.clock.step()
      c.io.PC.expect(0x1000000c)
    }
  }

  // ループで自身と同じアドレスに戻ってくるか
  // 書き込む命令
  // nop                00000013
  // LOOP:
  // beq zero,zero,LOOP 00000063
  it should "understand loop to self" in {
    test(
      new FetchWrapper(
        InstructionUtil.fromStringSeq32bit(Seq("00000013", "00000063"))
      )(defaultParams.copy(runParallel = 1))
    ).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.initialize()

      c.clock.step()
      c.io.PC.expect(0x10000004)

      c.setExecutorBranchResult(Seq(Some(0x10000004, 0)))
      c.clock.step()
      c.io.PC.expect(0x10000008)
      c.clock.step()
      c.io.PC.expect(0x10000004)
    }
  }

  // ループで同じ命令に戻る
  // 書き込む命令
  // LOOP:
  // nop                00000013
  // beq zero,zero,LOOP 00000063
  it should "understand loop" in {
    test(
      new FetchWrapper(
        InstructionUtil.fromStringSeq32bit(Seq("00000013", "00000063"))
      )(defaultParams.copy(runParallel = 1))
    ).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.initialize()
      c.clock.step()
      c.io.PC.expect(0x10000004)

      c.clock.step()
      c.io.PC.expect(0x10000008)

      c.clock.step()
      c.io.PC.expect(0x1000000c)

      c.setExecutorBranchResult(Seq(Some(0x10000000, 0)))

      c.clock.step(2)
      c.io.PC.expect(0x10000000)

      c.clock.step()
      c.io.PC.expect(0x10000004)
    }
  }

  // jalで無限ループ
  // 書き込む命令
  // LOOP:
  // nop                00000013
  // j                  ffdff06f
  it should "understand jal loop" in {
    test(
      new FetchWrapper(
        InstructionUtil.fromStringSeq32bit(Seq("00000013", "ffdff06f"))
      )
    ) { c =>
      c.initialize()
      c.clock.step()
      c.io.PC.expect(0x10000000)
    }
  }

  // jalで無条件アドレス替え
  // 書き込む命令
  // START:
  // nop      00000013
  // j LABEL  0180006f
  // nop      00000013
  // nop      00000013
  // nop      00000013
  // nop      00000013
  // nop      00000013
  // LABEL:
  // nop      00000013
  // j START  fe1ff06f
  it should "understand jal jumps" in {
    test(
      new FetchWrapper(
        InstructionUtil.fromStringSeq32bit(
          Seq(
            "00000013",
            "0180006f",
            "00000013",
            "00000013",
            "00000013",
            "00000013",
            "00000013",
            "00000013",
            "fe1ff06f"
          )
        )
      )
    ) { c =>
      c.initialize()
      c.clock.step()
      c.io.PC.expect(0x1000001c)

      c.clock.step()
      c.io.PC.expect(0x10000000)
    }
  }

  // Fenceを使う
  // 書き込む命令
  // nop    00000013
  // fence  0ff0000f
  // nop    00000013
  // nop    00000013
  it should "understand fence" in {
    test(
      new FetchWrapper(
        InstructionUtil.fromStringSeq32bit(
          Seq("00000013", "0ff0000f", "00000013", "00000013")
        )
      )
    ) { c =>
      c.initialize()
      c.io.decoders.decoder(0).valid.expect(true)
      c.io.decoders.decoder(1).valid.expect(true)
      c.clock.step()
      c.io.PC.expect(0x10000004)

      c.io.decoders.decoder(0).valid.expect(false)
      c.io.decoders.decoder(1).valid.expect(false)
      c.clock.step()
      c.io.PC.expect(0x10000004)

      c.io.reorderBufferEmpty.poke(true)
      c.io.decoders.decoder(0).valid.expect(false)
      c.io.decoders.decoder(1).valid.expect(false)
      c.clock.step()
      c.io.PC.expect(0x10000004)

      c.io.loadStoreQueueEmpty.poke(true)
      c.io.decoders.decoder(0).valid.expect(false)
      c.io.decoders.decoder(1).valid.expect(false)
      c.clock.step()
      c.io.PC.expect(0x10000008)

      c.io.decoders.decoder(0).valid.expect(true)
      c.io.decoders.decoder(1).valid.expect(true)
      c.clock.step()
      c.io.PC.expect(0x10000010)
    }
  }

  // ブランチを予測する
  // beq a0,a1, L2
  // L1:
  //	nop
  // L2:
  //    nop
  it should "predict a branch negative" in {
    test(
      new FetchWrapper(
        InstructionUtil
          .fromStringSeq32bit(Seq("00b50463", "00000013", "00000013"))
      )(defaultParams.copy(runParallel = 1))
    ).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.initialize()
      c.clock.step()
      c.io.PC.expect(0x10000004)
      c.clock.step()
      c.io.PC.expect(0x10000008)
    }
  }

  // ブランチを予測する
  // beq a0,a1, L2
  // L1:
  //	nop
  // L2:
  //    nop
  it should "predict a branch positive" in {
    test(
      new FetchWrapper(
        InstructionUtil
          .fromStringSeq32bit(Seq("00b50463", "00000013", "00000013"))
      )(defaultParams.copy(runParallel = 1))
    ).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.initialize()
      c.setPrediction(Seq(true))
      c.clock.step()
      c.io.PC.expect(0x10000008)
      c.clock.step()
      c.io.PC.expect(0x1000000c)
    }
  }

  // ブランチを予測する
  // beq a0,a1, L2
  // L1:
  //	nop
  // L2:
  //    nop
  it should "change address" in {
    test(
      new FetchWrapper(
        InstructionUtil
          .fromStringSeq32bit(Seq("00b50463", "00000013", "00000013"))
      )(defaultParams.copy(runParallel = 1))
    ).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.initialize()
      c.setPrediction(Seq(true))
      c.clock.step()
      c.io.PC.expect(0x10000008)

      c.setExecutorBranchResult(Seq(Some(0x10000004, 0)))

      c.clock.step(2)
      c.io.PC.expect(0x10000004)
    }
  }
}
