package b4processor.utils

import chisel3._
import chisel3.util._
import chisel3.internal.firrtl.Node
import net.jcazevedo.moultingyaml.Literal._

// Part I: Definitions for the actual data carried over AXI channels
// in part II we will provide definitions for the actual AXI interfaces
// by wrapping the part I types in Decoupled (ready/valid) bundles


// AXI Lite channel data definitions

class AXILiteAddress(addrWidthBits: Int) extends Bundle {
  val addr    = UInt(addrWidthBits.W)
  val prot    = UInt(3.W)
//  override def clone = { new AXILiteAddress(addrWidthBits).asInstanceOf[this.type] }
}

class AXILiteWriteData(dataWidthBits: Int) extends Bundle {
  val data    = UInt(dataWidthBits.W)
  val strb    = UInt((dataWidthBits/8).W)
//  override def clone = { new AXILiteWriteData(dataWidthBits).asInstanceOf[this.type] }
}

class AXILiteReadData(dataWidthBits: Int) extends Bundle {
  val data    = UInt(dataWidthBits.W)
  val resp    = UInt(2.W)
//  override def clone = { new AXILiteReadData(dataWidthBits).asInstanceOf[this.type] }
}

// Part II: Definitions for the actual AXI interfaces

//class AXILiteSlaveIF(addrWidthBits: Int, dataWidthBits: Int) extends Bundle {
//  // write address channel
//  val writeAddr   = Flipped(Decoupled(new AXILiteAddress(addrWidthBits)))
////  val writeAddr   = Decoupled(new AXILiteAddress(addrWidthBits)).flip
//  // write data channel
//  val writeData   = Flipped(Decoupled(new AXILiteWriteData(dataWidthBits)))
////  val writeData   = Decoupled(new AXILiteWriteData(dataWidthBits)).flip
//  // write response channel (for memory consistency)
//  val writeResp   = Decoupled(UInt(2.W))
//
//  // read address channel
//  val readAddr    = Flipped(Decoupled(new AXILiteAddress(addrWidthBits)))
////  val readAddr    = Decoupled(new AXILiteAddress(addrWidthBits)).flip
//  // read data channel
//  val readData    = Decoupled(new AXILiteReadData(dataWidthBits))
//
//  // rename signals to be compatible with those in the Xilinx template
//  def renameSignals() {
//    writeAddr.bits.addr.setName("S_AXI_AWADDR")
//    writeAddr.bits.prot.setName("S_AXI_AWPROT")
//    writeAddr.valid.setName("S_AXI_AWVALID")
//    writeAddr.ready.setName("S_AXI_AWREADY")
//    writeData.bits.data.setName("S_AXI_WDATA")
//    writeData.bits.strb.setName("S_AXI_WSTRB")
//    writeData.valid.setName("S_AXI_WVALID")
//    writeData.ready.setName("S_AXI_WREADY")
//    writeResp.bits.setName("S_AXI_BRESP")
//    writeResp.valid.setName("S_AXI_BVALID")
//    writeResp.ready.setName("S_AXI_BREADY")
//    readAddr.bits.addr.setName("S_AXI_ARADDR")
//    readAddr.bits.prot.setName("S_AXI_ARPROT")
//    readAddr.valid.setName("S_AXI_ARVALID")
//    readAddr.ready.setName("S_AXI_ARREADY")
//    readData.bits.data.setName("S_AXI_RDATA")
//    readData.bits.resp.setName("S_AXI_RRESP")
//    readData.valid.setName("S_AXI_RVALID")
//    readData.ready.setName("S_AXI_RREADY")
//  }
//
////  override def clone = { new AXILiteSlaveIF(addrWidthBits, dataWidthBits).asInstanceOf[this.type] }
//}



class AXILiteMasterIF(addrWidthBits: Int, dataWidthBits: Int) extends Bundle {  
  // write address channel
  val writeAddr   = Decoupled(new AXILiteAddress(addrWidthBits))
  // write data channel
  val writeData   = Decoupled(new AXILiteWriteData(dataWidthBits))
  // write response channel (for memory consistency)
  val writeResp   = Flipped(Decoupled(UInt(2.W)))
//  val writeResp   = Decoupled(UInt(2.W)).flip
  
  // read address channel
  val readAddr    = Decoupled(new AXILiteAddress(addrWidthBits))
  // read data channel
  val readData    = Flipped(Decoupled(new AXILiteReadData(dataWidthBits)))
  
  // rename signals to be compatible with those in the Xilinx template
//  def renameSignals() {
//    writeAddr.bits.addr.setName("M_AXI_AWADDR")
//    writeAddr.bits.prot.setName("M_AXI_AWPROT")
//    writeAddr.valid.setName("M_AXI_AWVALID")
//    writeAddr.ready.setName("M_AXI_AWREADY")
//    writeData.bits.data.setName("M_AXI_WDATA")
//    writeData.bits.strb.setName("M_AXI_WSTRB")
//    writeData.valid.setName("M_AXI_WVALID")
//    writeData.ready.setName("M_AXI_WREADY")
//    writeResp.bits.setName("M_AXI_BRESP")
//    writeResp.valid.setName("M_AXI_BVALID")
//    writeResp.ready.setName("M_AXI_BREADY")
//    readAddr.bits.addr.setName("M_AXI_ARADDR")
//    readAddr.bits.prot.setName("M_AXI_ARPROT")
//    readAddr.valid.setName("M_AXI_ARVALID")
//    readAddr.ready.setName("M_AXI_ARREADY")
//    readData.bits.data.setName("M_AXI_RDATA")
//    readData.bits.resp.setName("M_AXI_RRESP")
//    readData.valid.setName("M_AXI_RVALID")
//    readData.ready.setName("M_AXI_RREADY")
//  }

  /** FIXME setNameメソッドはchisel3から削除されているため使用できない
   *  従って，インターフェースをAXI接続にする必要のあるモジュールは継承クラスをModuleではなくMultiIOModuleにする必要がある？
   *  MultiIOModuleクラスはModuleクラスと違い，トップレベルのBundleをioにする必要がなく，好きな名前に設定できる
   */
  
//  override def clone = { new AXILiteMasterIF(addrWidthBits, dataWidthBits).asInstanceOf[this.type] }
}

