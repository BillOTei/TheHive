package org.thp.thehive.dto.v0

import play.api.libs.json._

case class InputChunkedAsset(
    flowChunkNumber: Int,
    flowChunkSize: Int,
    flowCurrentChunkSize: Int,
    flowTotalSize: Long,
    flowIdentifier: String,
    flowFilename: String,
    flowRelativePath: String,
    flowTotalChunks: Int
)

object InputChunkedAsset {
  implicit val format: OFormat[InputChunkedAsset] = Json.format[InputChunkedAsset]
}
