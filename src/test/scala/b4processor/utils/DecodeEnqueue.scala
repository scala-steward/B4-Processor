package b4processor.utils

import b4processor.structures.memoryAccess.MemoryAccessInfo

case class DecodeEnqueue(
  accessInfo: MemoryAccessInfo,
  addressTag: Int,
  storeDataTag: Int,
  storeData: Option[Long]
)
