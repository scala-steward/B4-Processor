package b4processor.utils

import b4processor.structures.memoryAccess.MemoryAccessInfo

case class LSQ2Memory(
  address: Int,
  tag: Int,
  data: Int,
  accessInfo: MemoryAccessInfo
)
