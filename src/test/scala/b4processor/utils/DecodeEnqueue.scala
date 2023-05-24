package b4processor.utils

case class DecodeEnqueue(
  operation: LoadStoreOperation.Type,
  addressTag: Int,
  storeDataTag: Int,
  storeData: Option[Long]
)
