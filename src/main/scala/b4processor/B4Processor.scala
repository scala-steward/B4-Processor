package b4processor

import b4processor.connections.{
  InstructionMemory2Cache,
  LoadStoreQueue2Memory,
  OutputValue
}
import b4processor.modules.branch_output_collector.BranchOutputCollector
import b4processor.modules.cache.{DataMemoryBuffer, InstructionMemoryCache}
import b4processor.modules.decoder.Decoder
import b4processor.modules.executor.Executor
import b4processor.modules.fetch.{Fetch, FetchBuffer}
import b4processor.modules.lsq.LoadStoreQueue
import b4processor.modules.memory.DataMemory
import b4processor.modules.ourputcollector.OutputCollector
import b4processor.modules.registerfile.RegisterFile
import b4processor.modules.reorderbuffer.ReorderBuffer
import b4processor.modules.reservationstation.ReservationStation
import chisel3._
import chisel3.experimental.FlatIO
import chisel3.stage.ChiselStage

class B4Processor(implicit params: Parameters) extends Module {
  val io = FlatIO(new Bundle {
    val instructionMemory = Flipped(new InstructionMemory2Cache)
    val dataMemory = new Bundle {
      val lsq = new LoadStoreQueue2Memory
      val output = Flipped(new OutputValue)
    }

    val registerFileContents =
      if (params.debug) Some(Output(Vec(31, UInt(64.W)))) else None
  })

  require(params.runParallel >= 1, "同時発行数は1以上である必要があります。")
  require(params.tagWidth >= 1, "タグ幅は1以上である必要があります。")
  require(params.fetchWidth >= 1, "フェッチ幅は1以上である必要があります。")
  require(
    params.maxRegisterFileCommitCount >= 1,
    "レジスタファイルへのコミット数は1以上である必要があります。"
  )

  val instructionCache = Module(new InstructionMemoryCache)
  val fetch = Module(new Fetch)
  val fetchBuffer = Module(new FetchBuffer)
  val reorderBuffer = Module(new ReorderBuffer)
  val registerFile = Module(new RegisterFile)
  val loadStoreQueue = Module(new LoadStoreQueue)
  val dataMemoryBuffer = Module(new DataMemoryBuffer)

  val outputCollector = Module(new OutputCollector)
  val branchAddressCollector = Module(new BranchOutputCollector)

  val decoders = (0 until params.runParallel).map(n => Module(new Decoder(n)))
  val reservationStations =
    Seq.fill(params.runParallel)(Module(new ReservationStation))
  val executors = Seq.fill(params.runParallel)(Module(new Executor))

  /** 出力コレクタとデータメモリ */
  outputCollector.io.dataMemory := io.dataMemory.output

  /** 命令メモリと命令キャッシュを接続 */
  io.instructionMemory <> instructionCache.io.memory

  /** フェッチとフェッチバッファの接続 */
  fetch.io.fetchBuffer <> fetchBuffer.io.fetch

  /** レジスタのコンテンツをデバッグ時に接続 */
  if (params.debug)
    io.registerFileContents.get <> registerFile.io.values.get

  /** デコーダ同士を接続 */
  for (i <- 1 until params.runParallel)
    decoders(i - 1).io.decodersAfter <> decoders(i).io.decodersBefore

  /** 命令キャッシュとフェッチを接続 */
  instructionCache.io.fetch <> fetch.io.cache

  for (i <- 0 until params.runParallel) {

    /** フェッチとデコーダの接続 */
    decoders(i).io.instructionFetch <> fetchBuffer.io.decoders(i)

    /** デコーダとリオーダバッファを接続 */
    decoders(i).io.reorderBuffer <> reorderBuffer.io.decoders(i)

    /** デコーダとリザベーションステーションを接続 */
    decoders(i).io.reservationStation <> reservationStations(i).io.decoder

    /** リザベーションステーションと実行ユニットを接続 */
    reservationStations(i).io.executor <> executors(i).io.reservationStation

    /** デコーダとレジスタファイルの接続 */
    decoders(i).io.registerFile <> registerFile.io.decoders(i)

    /** デコーダとLSQの接続 */
    decoders(i).io.loadStoreQueue <> loadStoreQueue.io.decoders(i)

    /** 出力コレクタと実行ユニットの接続 */
    for ((e, index) <- executors.zipWithIndex)
      outputCollector.io.executor(index) := e.io.out

    /** デコーダと出力コレクタ */
    decoders(i).io.outputCollector := outputCollector.io.outputs

    /** デコーダとLSQの接続 */
    loadStoreQueue.io.decoders(i) <> decoders(i).io.loadStoreQueue

    /** リザベーションステーションと実行ユニットの接続 */
    reservationStations(i).io.collectedOutput <> outputCollector.io.outputs

    /** 分岐結果コレクタと実行ユニットの接続 */
    branchAddressCollector.io.executor(i) := executors(i).io.fetch

  }

  /** フェッチと分岐結果の接続 */
  fetch.io.collectedBranchAddresses := branchAddressCollector.io.fetch

  /** LSQと出力コレクタ */
  loadStoreQueue.io.outputCollector := outputCollector.io.outputs

  /** レジスタファイルとリオーダバッファ */
  registerFile.io.reorderBuffer <> reorderBuffer.io.registerFile

  /** フェッチとLSQの接続 */
  fetch.io.loadStoreQueueEmpty := loadStoreQueue.io.isEmpty

  /** フェッチとリオーダバッファの接続 */
  fetch.io.reorderBufferEmpty := reorderBuffer.io.isEmpty

  // TODO:　必要ないはずだけど、確認が必要
  //  loadStoreQueue.io.reorderBuffer <> reorderBuffer.io.loadStoreQueue

  /** データメモリバッファとLSQ */
  dataMemoryBuffer.io.dataIn <> loadStoreQueue.io.memory

  /** データメモリとデータメモリバッファ */
//  TODO io.dataMemory.lsq <> dataMemoryBuffer.io.dataOut

  /** リオーダバッファと出力コレクタ */
  reorderBuffer.io.collectedOutputs := outputCollector.io.outputs

  /** リオーダバッファとLSQ */
  reorderBuffer.io.loadStoreQueue <> loadStoreQueue.io.reorderBuffer

  /** フェッチと分岐予測 TODO */
  fetch.io.prediction <> DontCare
}

object B4Processor extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(
    new B4Processor(),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
