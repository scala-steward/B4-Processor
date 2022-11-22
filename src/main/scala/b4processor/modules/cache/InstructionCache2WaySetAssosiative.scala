//package b4processor.modules.cache
//
//import b4processor.Parameters
//import b4processor.connections.{InstructionCache2Fetch, InstructionMemory2Cache}
//import b4processor.modules.memory.{
//  InstructionResponse
//}
//import chisel3._
//import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
//import chisel3.stage.ChiselStage
//import chisel3.util._
//
//import scala.math.pow
//
///** 命令キャッシュモジュール
//  *
//  * とても単純なキャッシュ機構
//  */
//class InstructionCache2WaySetAssosiative(cacheTagWidth: Int, offsetWidth: Int)
//    extends Module {
//  val io = IO(new Bundle {
//    /** フェッチ */
//    val fetch = Vec(params.runParallel, new InstructionCache2Fetch)
//
//    val memory = new Bundle {
//      val request = Irrevocable(new InstructionFetchTransaction())
//      val response = Flipped(Valid(new InstructionResponse))
//    }
//  })
//
//  private val buf = RegInit(
//    VecInit(Seq.fill(4)(new Bundle {}.Lit(_.valid -> false.B)))
//  )
//
//  private val request = WireDefault(0.U(60.W))
//  private var didRequest = false.B
//  for (f <- io.fetch) {
//    val lowerAddress = f.address.bits(63, 1)
//    val upperAddress = lowerAddress + 1.U
//    val lowerData = WireDefault(0.U(16.W))
//    val upperData = WireDefault(0.U(16.W))
//    f.output.valid := false.B
//    f.output.bits := 0.U
//    var foundData = false.B
//    for (b <- buf) {
//      when(!foundData && b.valid && lowerAddress(62, 3) === b.upper) {
//        lowerData := b.data(lowerAddress(2, 0))
//      }
//      foundData = foundData || (b.valid && lowerAddress(62, 3) === b.upper)
//    }
//
//    var foundData2 = false.B
//    for (b <- buf) {
//      when(!foundData2 && b.valid && upperAddress(62, 3) === b.upper) {
//        upperData := b.data(upperAddress(2, 0))
//      }
//      foundData2 = foundData2 || (b.valid && upperAddress(62, 3) === b.upper)
//    }
//
//    f.output.valid := foundData && foundData2
//    f.output.bits := upperData ## lowerData
//
//    when(f.address.valid) {
//      when(!foundData && !didRequest) {
//        request := lowerAddress(62, 3)
//      }.elsewhen(!foundData2 && !didRequest) {
//        request := upperAddress(62, 3)
//      }
//    }
//    didRequest = didRequest || (!foundData || !foundData2) && f.address.valid
//  }
//
//  private val waiting :: requesting :: Nil = Enum(2)
//  private val state = RegInit(waiting)
//  private val readIndex = Reg(UInt(1.W))
//  private val requested = Reg(Bool())
//  private val transaction = Reg(new InstructionFetchTransaction)
//  private val requestDone = Reg(Bool())
//
//  when(didRequest && state === waiting) {
//    state := requesting
//    requested := false.B
//    readIndex := 0.U
//
//    val tmp_transaction =
//      Wire(new InstructionFetchTransaction)
//    tmp_transaction.address := Cat(request, readIndex, 0.U(3.W))
//    tmp_transaction.burstLength := 1.U
//    transaction := tmp_transaction
//    io.memory.request.valid := true.B
//    io.memory.request.bits := tmp_transaction
//    requestDone := false.B
//  }
//
//  io.memory.request.valid := false.B
//  io.memory.request.bits := DontCare
//  private val head = RegInit(0.U(2.W))
//  when(state === requesting) {
//    when(!requestDone) {
//      io.memory.request.valid := true.B
//      io.memory.request.bits := transaction
//      when(io.memory.request.ready) {
//        requestDone := true.B
//      }
//    }
//
//    when(io.memory.response.valid) {
//      buf(head).valid := false.B
//      for (i <- 0 until 4) {
//        buf(head).data(readIndex ## i.U(2.W)) := io.memory.response.bits
//          .inner(i * 16 + 15, i * 16)
//      }
//      readIndex := readIndex + 1.U
//      when(readIndex === 1.U) {
//        state := waiting
//        buf(head).valid := true.B
//        buf(head).upper := request
//        head := head + 1.U
//      }
//    }
//  }
//
//}
//
//object InstructionMemoryCache extends App {
//  implicit val params = Parameters()
//  (new ChiselStage).emitVerilog(
//    new InstructionMemoryCache(),
//    args = Array(
//      "--emission-options=disableMemRandomization,disableRegisterRandomization"
//    )
//  )
//}
//
//class Way(tagWidth: Int, offsetWidth: Int) extends Module {
//  val read = IO(new Bundle {
//    val addressTag = Input(UInt(tagWidth.W))
//    val set = Output(UInt(tagWidth.W))
//    val cacheHit = Output(Bool())
//  })
//
//  val update = IO(Flipped(Valid(new Bundle {
//    val addressUpper = UInt((64 - tagWidth - offsetWidth).W)
//    val tagListIndex = UInt(tagWidth.W)
//  })))
//
//  private val tagList = RegInit(
//    VecInit(Seq.fill(pow(2, tagWidth).toInt)(new Bundle {
//      val valid = Bool()
//      val entry = UInt((64 - tagWidth - offsetWidth).W)
//    }))
//  )
//
//  read.cacheHit :=
//    tagList
//      .map(p => p.valid && p.entry === read.addressTag)
//      .reduce(_ | _)
//  read.set := Mux1H(
//    (0 until pow(2, tagWidth).toInt)
//      .zip(tagList)
//      .map(i => (i._2.valid && i._2.entry === read.addressTag) -> i._1.U)
//  )
//
//  when(update.valid) {
//    tagList(update.bits.tagListIndex) := update.bits.addressUpper
//  }
//}
//
//class Set(tagWidth: Int, offsetWidth: Int, blockSize: Int = 64) extends Module {
//  val read = IO(new Bundle {
//    val addressOffset = Input(UInt(offsetWidth.W))
//    val set = Input(UInt(tagWidth.W))
//    val output = Output(UInt(blockSize.W))
//  })
//
//  val update = IO(Flipped(Valid(new Bundle {
//    val offset = Input(UInt(offsetWidth.W))
//    val set = Input(UInt(tagWidth.W))
//    val data = Input(UInt(blockSize.W))
//  })))
//
//  private val set = RegInit(
//    VecInit(
//      Seq.fill(pow(2, tagWidth).toInt)(
//        VecInit(Seq.fill(pow(2, offsetWidth).toInt)(UInt(blockSize.W)))
//      )
//    )
//  )
//
//  read.output := set(read.set)(read.addressOffset)
//
//  when(update.valid) {
//    set(update.bits.set)(update.bits.offset) := update.bits.data
//  }
//}
