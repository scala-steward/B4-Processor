//package b4processor.modules.decoder
//
//import chisel3._
//import chisel3.util._
//import chisel3.util.experimental.decode._
//
//
//case class Pattern(val name: String, val code: BitPat) extends DecodePattern {
//  def bitPat: BitPat = code
//}
//
//object ArithmeticOperationEnum extends ChiselEnum with DecodeField[Pattern, ArithmeticOperationEnum.Type]{
//  val ADD,SUB = Value
//
//  override def name: String = "Arithmetic operation"
//
//  override def genTable(op: Pattern): BitPat = ArithmeticOperationEnum.ADD
//}
//
//class ArithmeticOperation extends Bundle {
//  val operation = ArithmeticOperationEnum.Type
//}
//
//object LsqOperationEnum extends ChiselEnum {
//  val store8,store16,store32,store64,load8,load16,load32,load64,load8unsigned,load16unsigned,load32unsigned = Value
//}
//
//class LsqOperation extends Bundle {
//  val operation = LsqOperationEnum.Type
//}
//
//object  CSROperationEnum extends ChiselEnum {
//  val readWrite,readClear,readSet = Value
//}
//class CSROperation extends Bundle {
//  val operation = CSROperationEnum.Type
//}
//
//class DecodedValue extends Bundle {
//  val destination = UInt(5.W)
//  val source1 = UInt(5.W)
//  val source2 = UInt(5.W)
//  val funct3 = UInt(3.W)
//  val funct7 = UInt(7.W)
//  val arithmeticOperation = new ArithmeticOperation
//  val lsqOperation = new LsqOperation
//  val csrOperation = new CSROperation
//}