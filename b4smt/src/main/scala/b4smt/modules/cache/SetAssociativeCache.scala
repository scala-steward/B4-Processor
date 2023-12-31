//package b4processor.modules.cache
//
//import b4processor.Parameters
//import b4processor.connections.InstructionCache2Fetch
//import b4processor.modules.memory.{InstructionResponse, MemoryReadTransaction}
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
//class SetAssociativeCache(tagWidth: Int = 2, offsetWidth: Int = 3)(implicit
//  params: Parameters
//) extends Module {
//  val blockWidth = 3 // 64bitごとに保存するのでメモリのうち3bitを省略する
//
//  val io = IO(new Bundle {
//
//    /** フェッチ */
//    val fetch = Vec(params.runParallel, new InstructionCache2Fetch)
//
//    val memory = new Bundle {
//      val request = Irrevocable(new MemoryReadTransaction())
//      val response = Flipped(Valid(new InstructionResponse))
//    }
//  })
//
//  class TagMapEntry extends Bundle {
//    val valid = Bool()
//    val addressUpper = UInt((64 - tagWidth - offsetWidth - blockWidth).W)
//  }
//  object TagMapEntry {
//    def default: TagMapEntry = {
//      val w = new TagMapEntry()
//      w.valid := false.B
//      w.addressUpper := DontCare
//      w
//    }
//  }
//  private val tagMap = RegInit(
//    VecInit(Seq.fill(pow(2, tagWidth).toInt)(TagMapEntry.default))
//  )
//  private val blocks =
//    Vec(pow(2, tagWidth).toInt, Vec(pow(2, offsetWidth).toInt, UInt(64.W)))
//  private val fetchBaseAddress = io.fetch(0).address.bits
//
//  val tag1 =
//    fetchBaseAddress(
//      tagWidth + offsetWidth + blockWidth - 1,
//      offsetWidth + blockWidth
//    )
//  val tag2 =
//    (fetchBaseAddress(63, offsetWidth + blockWidth) + 1.U)(tagWidth - 1, 0)
//  val offset = fetchBaseAddress(offsetWidth + blockWidth - 1, blockWidth)
//  val entryValid1 =
//    tagMap(tag1).addressUpper === fetchBaseAddress(
//      63,
//      tagWidth + offsetWidth + blockWidth
//    )
//  val entryValid2 =
//    tagMap(tag2).addressUpper === fetchBaseAddress(
//      63,
//      tagWidth + offsetWidth + blockWidth
//    )
//  val b1 =
//    blocks(tag1)(offset)
//  val b2 =
//    Mux(offset.asSInt =/= -1.S, blocks(tag1)(offset + 1.U), blocks(tag2)(0))
//
//  // outputMap にbaseFetchAddressから128bitを用意する
//  val outputMap = Vec(pow(2, blockWidth).toInt, UInt(16.W))
//  for (i <- 0 until pow(2, blockWidth - 1).toInt) {
//    outputMap(i) := b1(i * 16 + 15, i * 16)
//  }
//  for (i <- 0 until pow(2, blockWidth - 1).toInt) {
//    outputMap(i + 4) := b1(i * 16 + 15, i * 16)
//  }
//
//  for (f <- io.fetch) {
//    when(
//      f.address.bits(
//        63,
//        tagWidth + offsetWidth + blockWidth
//      ) === fetchBaseAddress(63, tagWidth + offsetWidth + blockWidth)
//    ) {
//      when(
//        f.address.bits(
//          tagWidth + offsetWidth + blockWidth - 1,
//          offsetWidth + blockWidth
//        ) === fetchBaseAddress(
//          tagWidth + offsetWidth + blockWidth - 1,
//          offsetWidth + blockWidth
//        )
//      ){
//        throw new RuntimeException("not implemented")
//      }
//    }
//  }
//
//}
//
//object SetAssociativeCache extends App {
//  implicit val params = Parameters()
//  (new ChiselStage).emitVerilog(
//    new SetAssociativeCache(),
//    args = Array(
//      "--emission-options=disableMemRandomization,disableRegisterRandomization"
//    )
//  )
//}
