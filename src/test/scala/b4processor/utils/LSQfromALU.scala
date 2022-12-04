package b4processor.utils

import b4processor.connections.ResultType

case class LSQfromALU(
  destinationtag: Int,
  value: Int,
  valid: Boolean,
  resultType: ResultType.Type = ResultType.Result
)
