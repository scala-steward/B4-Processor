package b4processor.utils

import b4processor.{B4Processor, Parameters}
import chisel3._
import chiseltest._
import chisel3.stage.ChiselStage
import chisel3.util.Valid

class B4ProcessorWithMemory(instructions: String)(implicit params: Parameters)
    extends Module {
  val io = IO(new Bundle {
    val simulation = Flipped(Valid(UInt(64.W)))
    val registerFileContents = if (params.debug) Some(Output(Vec(32, UInt(64.W)))) else None
    val accessMemoryAddress = new Bundle {
      val read = Valid(UInt(64.W))
      val write = Valid(UInt(64.W))
      val writeData = Valid(UInt(64.W))
    }
  })
  val core = Module(new B4Processor)
  val axiMemory = Module(new SimpleAXIMemory())
  core.io.axi <> axiMemory.axi
  io.simulation <> axiMemory.simulationSource.input

  io.registerFileContents.get <> core.io.registerFileContents.get

  io.accessMemoryAddress.read.valid := core.io.axi.readAddress.valid
  io.accessMemoryAddress.read.bits := core.io.axi.readAddress.bits.ADDR
  io.accessMemoryAddress.write.valid := core.io.axi.writeAddress.valid
  io.accessMemoryAddress.write.bits := core.io.axi.writeAddress.bits.ADDR
  io.accessMemoryAddress.writeData.valid := core.io.axi.write.valid
  io.accessMemoryAddress.writeData.bits := core.io.axi.write.bits.DATA

  def initialize(): Unit = {
    val memoryInit = InstructionUtil.fromFile8bit(instructions + ".text.hex")
    this.io.simulation.valid.poke(true)
    this.io.simulation.bits.poke(memoryInit.length)
    for (i <- memoryInit.indices) {
      this.clock.step()
      this.io.simulation.bits.poke(memoryInit(i))
    }
    this.clock.step()
    this.io.simulation.valid.poke(false)
    this.clock.step()
  }

  def checkForWrite(address: UInt, value: UInt, timeout: Int = 500): Unit = {
    this.clock.setTimeout(timeout)
    while (this.io.accessMemoryAddress.write.valid.peekBoolean() && this.io.accessMemoryAddress.write.bits.peek() == address)
      this.clock.step()
    this.io.accessMemoryAddress.write.bits.expect(address)
    while (this.io.accessMemoryAddress.writeData.valid.peekBoolean() && this.io.accessMemoryAddress.writeData.bits.peek() == value)
      this.clock.step()
    this.io.accessMemoryAddress.write.bits.expect(value)
    this.clock.step(10)
  }

  def checkForRegister(regNum: Int, value: UInt, timeout: Int = 500): Unit = {
    this.clock.setTimeout(timeout)
    while (this.io.registerFileContents.get(regNum).peek() != value)
      this.clock.step()
    this.io.registerFileContents.get(regNum).expect(value)
    this.clock.step(3)
  }
}

object B4ProcessorWithMemory extends App {
  implicit val params = Parameters(
    debug = true,
    runParallel = 2,
    maxRegisterFileCommitCount = 4,
    tagWidth = 5,
    loadStoreQueueIndexWidth = 3
  )
  (new ChiselStage).emitVerilog(
    new B4ProcessorWithMemory("riscv-sample-programs/fibonacci_c/fibonacci_c"),
    args = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
