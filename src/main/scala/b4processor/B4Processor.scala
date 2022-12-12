package b4processor

import b4processor.connections.{InstructionMemory2Cache, LoadStoreQueue2Memory, OutputValue}
import b4processor.modules.branch_output_collector.BranchOutputCollector
import b4processor.modules.cache.{DataMemoryBuffer, InstructionMemoryCache}
import b4processor.modules.decoder.Decoder
import b4processor.modules.executor.Executor
import b4processor.modules.fetch.{Fetch, FetchBuffer}
import b4processor.modules.lsq.LoadStoreQueue
import b4processor.modules.memory.ExternalMemoryInterface
import b4processor.modules.ourputcollector.OutputCollector
import b4processor.modules.registerfile.RegisterFile
import b4processor.modules.reorderbuffer.ReorderBuffer
import b4processor.modules.reservationstation.ReservationStation
import b4processor.utils.AXI
import chisel3._
import chisel3.experimental.FlatIO
import chisel3.stage.ChiselStage

class B4Processor(implicit params: Parameters) extends Module {
  val axi = IO(new AXI(64, 64))
  val registerFileContents =
    if (params.debug) Some(IO(Output(Vec(params.threads, Vec(32, UInt(64.W))))))
    else None

  require(params.decoderPerThread >= 1, "スレッド毎にデコーダは1以上必要です。")
  require(params.threads >= 1, "スレッド数は1以上です。")
  require(params.tagWidth >= 1, "タグ幅は1以上である必要があります。")
  require(
    params.maxRegisterFileCommitCount >= 1,
    "レジスタファイルへのコミット数は1以上である必要があります。"
  )

  val instructionCache =
    (0 until params.threads).map(n => Module(new InstructionMemoryCache(n)))
  val fetch = (0 until params.threads).map(n => Module(new Fetch(n)))
  val fetchBuffer = (0 until params.threads).map(n => Module(new FetchBuffer))
  val reorderBuffer =
    (0 until params.threads).map(n => Module(new ReorderBuffer(n)))
  val registerFile =
    (0 until params.threads).map(n => Module(new RegisterFile(n)))
  val loadStoreQueue =
    (0 until params.threads).map(n => Module(new LoadStoreQueue))
  val dataMemoryBuffer = Module(new DataMemoryBuffer)

  val outputCollector = Module(new OutputCollector)
  val branchAddressCollector = Module(new BranchOutputCollector)

  val decoders = (0 until params.threads).map(tid =>
    (0 until params.decoderPerThread).map(n => Module(new Decoder(n, tid)))
  )
  val reservationStation = Module(new ReservationStation)
  val executor = Module(new Executor)

  val externalMemoryInterface = Module(new ExternalMemoryInterface)

  axi <> externalMemoryInterface.io.coordinator

  /** 出力コレクタとデータメモリ */
  outputCollector.io.dataMemory <> externalMemoryInterface.io.dataReadOut

  /** レジスタのコンテンツをデバッグ時に接続 */
  if (params.debug) {
    for (tid <- 0 until params.threads)
      for (i <- 0 until 32)
        registerFileContents.get(tid)(i) <> registerFile(tid).io.values.get(i)
  }

  /** デコーダ同士を接続 */
  for (tid <- 0 until params.threads)
    for (i <- 1 until params.decoderPerThread)
      decoders(tid)(i - 1).io.decodersAfter <>
        decoders(tid)(i).io.decodersBefore

  /** リザベーションステーションと実行ユニットを接続 */
  reservationStation.io.executor <> executor.io.reservationStation

  /** 出力コレクタと実行ユニットの接続 */
  outputCollector.io.executor <> executor.io.out

  /** リザベーションステーションと実行ユニットの接続 */
  reservationStation.io.collectedOutput <> outputCollector.io.outputs

  /** 分岐結果コレクタと実行ユニットの接続 */
  branchAddressCollector.io.executor <> executor.io.fetch

  for (tid <- 0 until params.threads) {

    /** 命令キャッシュとフェッチを接続 */
    instructionCache(tid).io.fetch <> fetch(tid).io.cache

    /** フェッチとフェッチバッファの接続 */
    fetch(tid).io.fetchBuffer <> fetchBuffer(tid).io.fetch

    /** フェッチとデコーダの接続 */
    for (d <- 0 until params.decoderPerThread) {
      decoders(tid)(d).io.instructionFetch <> fetchBuffer(tid).io.decoders(d)

      /** デコーダとリオーダバッファを接続 */
      decoders(tid)(d).io.reorderBuffer <> reorderBuffer(tid).io.decoders(d)

      /** デコーダとリザベーションステーションを接続 */
      decoders(tid)(d).io.reservationStation <>
        reservationStation.io.decoder(tid * params.decoderPerThread + d)

      /** デコーダとレジスタファイルの接続 */
      decoders(tid)(d).io.registerFile <> registerFile(tid).io.decoders(d)

      /** デコーダとLSQの接続 */
      decoders(tid)(d).io.loadStoreQueue <> loadStoreQueue(tid).io.decoders(d)

      /** デコーダと出力コレクタ */
      decoders(tid)(d).io.outputCollector := outputCollector.io.outputs

      /** デコーダとLSQの接続 */
      loadStoreQueue(tid).io.decoders(d) <> decoders(tid)(d).io.loadStoreQueue
    }

    /** フェッチと分岐結果の接続 */
    fetch(tid).io.collectedBranchAddresses := branchAddressCollector.io.fetch

    /** LSQと出力コレクタ */
    loadStoreQueue(tid).io.outputCollector := outputCollector.io.outputs

    /** レジスタファイルとリオーダバッファ */
    registerFile(tid).io.reorderBuffer <> reorderBuffer(tid).io.registerFile

    /** フェッチとLSQの接続 */
    fetch(tid).io.loadStoreQueueEmpty := loadStoreQueue(tid).io.isEmpty

    /** フェッチとリオーダバッファの接続 */
    fetch(tid).io.reorderBufferEmpty := reorderBuffer(tid).io.isEmpty

    /** データメモリバッファとLSQ */
    dataMemoryBuffer.io.dataIn(tid) <> loadStoreQueue(tid).io.memory

    /** リオーダバッファと出力コレクタ */
    reorderBuffer(tid).io.collectedOutputs := outputCollector.io.outputs

    /** リオーダバッファとLSQ */
    reorderBuffer(tid).io.loadStoreQueue <> loadStoreQueue(tid).io.reorderBuffer

    /** 命令メモリと命令キャッシュを接続 */
    externalMemoryInterface.io.instructionFetchRequest(tid) <> instructionCache(
      tid
    ).io.memory.request
    externalMemoryInterface.io.instructionOut(tid) <> instructionCache(
      tid
    ).io.memory.response

    /** フェッチと分岐予測 TODO */
    fetch(tid).io.prediction <> DontCare
  }

  /** メモリとデータメモリバッファ */
  externalMemoryInterface.io.dataReadRequests <> dataMemoryBuffer.io.dataReadRequest
  externalMemoryInterface.io.dataWriteRequests <> dataMemoryBuffer.io.dataWriteRequest
}

object B4Processor extends App {
  implicit val params = Parameters(
    threads = 2,
    decoderPerThread = 2,
    instructionStart = 0x2000_0000L
  )
  (new ChiselStage).emitVerilog(
    new B4Processor()
  )
}
