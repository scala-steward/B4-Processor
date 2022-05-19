package b4processor

import b4processor.connections.{DataMemory2Cache, Decoder2NextDecoder, InstructionMemory2Cache}
import b4processor.modules.cache.InstructionMemoryCache
import b4processor.modules.decoder.Decoder
import b4processor.modules.executor.Executor
import b4processor.modules.fetch.Fetch
import b4processor.modules.lsq.LoadStoreQueue
import b4processor.modules.registerfile.RegisterFile
import b4processor.modules.reorderbuffer.ReorderBuffer
import b4processor.modules.reservationstation.ReservationStation
import chisel3._
import chisel3.experimental.FlatIO
import chisel3.stage.ChiselStage

class B4Processor(implicit params: Parameters) extends Module {
  val io = FlatIO(new Bundle {
    val instructionMemory = Flipped(new InstructionMemory2Cache)
    val dataMemory = Flipped(new DataMemory2Cache)

    val registerFileContents = if (params.debug) Some(Output(Vec(31, UInt(64.W)))) else None
  })

  val instructionCache = Module(new InstructionMemoryCache)
  val fetch = Module(new Fetch)
  val reorderBuffer = Module(new ReorderBuffer)
  val registerFile = Module(new RegisterFile)
  val loadStoreQueue = Module(new LoadStoreQueue)

  val decoders = (0 until params.runParallel).map(n => Module(new Decoder(n)))
  val reservationStations = Seq.fill(params.runParallel)(Module(new ReservationStation))
  val executors = Seq.fill(params.runParallel)(Module(new Executor))

  /** 命令メモリと命令キャッシュを接続 */
  io.instructionMemory <> instructionCache.io.memory

  /** データキャッシュは現在無効 :TODO */
  io.dataMemory <> DontCare

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
    decoders(i).io.instructionFetch <> fetch.io.decoders(i)

    /** デコーダとリオーダバッファを接続 */
    decoders(i).io.reorderBuffer <> reorderBuffer.io.decoders(i)

    /** デコーダとリザベーションステーションを接続 */
    decoders(i).io.reservationStation <> reservationStations(i).io.decoder

    /** リザベーションステーションと実行ユニットを接続 */
    reservationStations(i).io.executor <> executors(i).io.reservationstation

    /** 実行ユニットとリオーダバッファを接続 */
    executors(i).io.out <> reorderBuffer.io.executors(i)

    /** デコーダとレジスタファイルの接続 */
    decoders(i).io.registerFile <> registerFile.io.decoders(i)

    /** デコーダと実行ユニットの接続 */
    for ((e, index) <- executors.zipWithIndex)
      decoders(i).io.executors(index) <> e.io.out

    /** デコーダとLSQの接続 */
    loadStoreQueue.io.decoders(i) <> decoders(i).io.loadStoreQueue

    /** リザベーションステーションと実行ユニットの接続 */
    for ((e, index) <- executors.zipWithIndex)
      reservationStations(i).io.bypassValues(index) <> e.io.out

    /** LSQと実行ユニットの接続 */
    executors(i).io.loadStoreQueue <> loadStoreQueue.io.executors(i)

    /** フェッチと実行ユニットの接続 */
    fetch.io.executorBranchResult(i) <> executors(i).io.fetch
  }

  /** レジスタファイルとリオーダバッファ */
  registerFile.io.reorderBuffer <> reorderBuffer.io.registerFile

  /** フェッチとLSQの接続 */
  fetch.io.loadStoreQueueEmpty := loadStoreQueue.io.isEmpty

  /** フェッチとリオーダバッファの接続 */
  fetch.io.reorderBufferEmpty := reorderBuffer.io.isEmpty

  /** フェッチと分岐予測 TODO */
  fetch.io.prediction <> DontCare

  /** LSQとへの接続  TODO */
  loadStoreQueue.io <> DontCare
}

object B4Processor extends App {
  implicit val params = Parameters()
  (new ChiselStage).emitVerilog(new B4Processor(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}
