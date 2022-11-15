//package b4processor.modules.memory
//
//import b4processor.Parameters
//import b4processor.connections.{LoadStoreQueue2Memory, OutputValue}
//import b4processor.structures.memoryAccess.MemoryAccessType._
//import b4processor.structures.memoryAccess.MemoryAccessWidth.{DoubleWord, _}
//import b4processor.utils.InstructionUtil
//import chisel3.{RegNext, _}
//import chisel3.util._
//import chisel3.stage.ChiselStage
//import chisel3.util.experimental.loadMemoryFromFileInline
//import chisel3.experimental.{ChiselAnnotation, annotate}
//import firrtl.annotations.{Annotation, MemorySynthInit}
//
//class DataMemory(instructions: String)(implicit params: Parameters)
//    extends Module {
//  val io = IO(new Bundle {
//    val dataIn = Flipped(new LoadStoreQueue2Memory)
//    val dataOut = new OutputValue
//  })
//
//  // Notice the annotation below
//  annotate(new ChiselAnnotation {
//    override def toFirrtl = MemorySynthInit
//  })
//
//  val mem = Seq.fill(8)(SyncReadMem(params.dataMemorySize / 8, UInt(8.W)))
//
//  // Initialize memory
//  if (instructions.trim().nonEmpty) {
//    for ((m, i) <- mem.zipWithIndex)
//      loadMemoryFromFileInline(m, s"${instructions}.data_${i}.hex")
//  }
//
//  io.dataOut.value := DontCare
//
//  val isLoad = io.dataIn.valid && io.dataIn.bits.accessInfo.accessType === Load
//  val waitForLoad = RegInit(false.B)
//  waitForLoad := isLoad && !waitForLoad
//  io.dataIn.ready := !waitForLoad
//  val input = Mux(waitForLoad, RegNext(io.dataIn.bits), io.dataIn.bits)
//  val willOutput = WireDefault(false.B)
//  when(io.dataIn.valid || waitForLoad) {
//    // FIXME: アドレスを下位28bitのみ使っている
//    val address = input.address
//    val rdwrPort = mem.map(m => m(address(27, 3)))
//    when(io.dataIn.valid && input.accessInfo.accessType === Store) {
//      val storeData = input.data
//      when(input.accessInfo.accessWidth === DoubleWord) {
//        for (j <- 0 until 8)
//          rdwrPort(j) := storeData((j + 1) * 8 - 1, j * 8)
//      }.elsewhen(input.accessInfo.accessWidth === Word) {
//        for (i <- 0 until 2)
//          when(address(2) === i.U) {
//            for (j <- 0 until 4)
//              rdwrPort(i * 4 + j) := storeData((j + 1) * 8 - 1, j * 8)
//          }
//      }.elsewhen(input.accessInfo.accessWidth === HalfWord) {
//        for (i <- 0 until 4)
//          when(address(2, 1) === i.U) {
//            for (j <- 0 until 2)
//              rdwrPort(i * 2 + j) := storeData((j + 1) * 8 - 1, j * 8)
//          }
//      }.elsewhen(input.accessInfo.accessWidth === Byte) {
//        for (i <- 0 until 8)
//          when(address(2, 0) === i.U) {
//            rdwrPort(i) := storeData(7, 0)
//          }
//      }
//    }
//    // Load 出てくる出力が1クロック遅れているのでRegNextを使う
//    when(waitForLoad) {
//      willOutput := true.B
//      val outUnsigned: UInt = MuxLookup(
//        input.accessInfo.accessWidth.asUInt,
//        0.U,
//        Seq(
//          Byte.asUInt -> MuxLookup(
//            address(2, 0),
//            0.U,
//            (0 until 8).map(i => i.U -> rdwrPort(i))
//          ),
//          HalfWord.asUInt -> MuxLookup(
//            address(2, 1),
//            0.U,
//            (0 until 4).map(i => i.U -> rdwrPort(i * 2 + 1) ## rdwrPort(i * 2))
//          ),
//          Word.asUInt -> MuxLookup(
//            address(2),
//            0.U,
//            (0 until 2).map(i =>
//              i.U -> rdwrPort(i * 4 + 3) ##
//                rdwrPort(i * 4 + 2) ##
//                rdwrPort(i * 4 + 1) ##
//                rdwrPort(i * 4)
//            )
//          ),
//          DoubleWord.asUInt -> rdwrPort(7) ##
//            rdwrPort(6) ##
//            rdwrPort(5) ##
//            rdwrPort(4) ##
//            rdwrPort(3) ##
//            rdwrPort(2) ##
//            rdwrPort(1) ##
//            rdwrPort(0)
//        )
//      )
//
//      when(input.accessInfo.accessWidth === HalfWord) {
//        assert(address(0) === 0.U, "half-word not aligned")
//      }
//      when(input.accessInfo.accessWidth === Word) {
//        assert(address(1, 0) === 0.U, "word not aligned")
//      }
//      when(input.accessInfo.accessWidth === DoubleWord) {
//        assert(address(2, 0) === 0.U, "double-word not aligned")
//      }
//
//      val outExtended = Wire(SInt(64.W))
//      outExtended := Mux(
//        !input.accessInfo.signed,
//        outUnsigned.asSInt,
//        MuxLookup(
//          input.accessInfo.accessWidth.asUInt,
//          outUnsigned.asSInt,
//          Seq(
//            Byte.asUInt -> outUnsigned(7, 0).asSInt,
//            HalfWord.asUInt -> outUnsigned(15, 0).asSInt,
//            Word.asUInt -> outUnsigned(31, 0).asSInt
//          )
//        )
//      )
//
//      io.dataOut.value := outExtended.asUInt
//    }
//    // printf(p"rdwrPort =${rdwrPort}\n")
//    // printf(p"rdwrPort(7, 0) = ${rdwrPort(7, 0)}\n")
//    // printf(p"dataOut = ${io.dataOut.value}\n")
//  }
//  //  printf(p"io.dataOut.validasResult = ${io.dataOut.validAsResult}\n")
//  //  printf(p"io.dataOut.tag = ${io.dataOut.tag}\n")
//  //  printf(p"io.dataOut.value = ${io.dataOut.value}\n\n")
//  io.dataOut.tag := Mux(willOutput, input.tag, 0.U)
//  io.dataOut.validAsResult := willOutput
//  io.dataOut.validAsLoadStoreAddress := false.B
//  // printf(p"mem(io.dataIn.bits.address.asUInt) = ${mem(io.dataIn.bits.address.asUInt)}\n")
//}
//
//object DataMemory extends App {
//  implicit val params =
//    Parameters(runParallel = 1, maxRegisterFileCommitCount = 1, tagWidth = 4)
//  (new ChiselStage).emitVerilog(
//    new DataMemory(
//      instructions = "riscv-sample-programs/fibonacci_c/fibonacci_c.data.hex"
//    ),
//    args = Array(
//      "--emission-options=disableMemRandomization,disableRegisterRandomization"
//    )
//  )
//}
