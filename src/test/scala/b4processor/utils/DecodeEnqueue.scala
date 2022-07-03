package b4processor.utils

case class DecodeEnqueue(opcode: Int,
                         function3: Int,
                         addressTag: Int,
                         storeDataTag: Int,
                         storeData: Option[Long])
