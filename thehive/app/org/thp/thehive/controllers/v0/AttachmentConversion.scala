package org.thp.thehive.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.thehive.dto.v0.{InputChunkedAsset, OutputAttachment, OutputChunkedAsset}
import org.thp.thehive.models.Attachment

import scala.language.implicitConversions

object AttachmentConversion {
  implicit def toOutputAttachment(attachment: Attachment): Output[OutputAttachment] =
    Output[OutputAttachment](
      attachment
        .into[OutputAttachment]
        .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
        .withFieldComputed(_.id, _.attachmentId)
        .transform
    )

  implicit def fromInputChunkedAsset(inputChunkedAsset: InputChunkedAsset): Attachment =
    inputChunkedAsset
      .into[Attachment]
      .withFieldComputed(_.name, _.flowFilename)
      .withFieldComputed(_.attachmentId, _.flowIdentifier)
      .withFieldConst(_.contentType, "application/octet-stream")
      .withFieldComputed(_.size, _.flowTotalSize)
      .withFieldConst(_.hashes, Nil)
      .withFieldConst(_.remainingChunks, None)
      .withFieldComputed(_.totalChunks, i => Some(i.flowTotalChunks))
      .transform

  def toOutputChunkAsset(attachment: Attachment): OutputChunkedAsset =
    attachment
      .into[OutputChunkedAsset]
      .withFieldComputed(_.flowIdentifier, _.attachmentId)
      .withFieldComputed(_.flowFilename, _.name)
      .withFieldComputed(_.flowRemainingChunks, _.remainingChunks.getOrElse(-999))
      .withFieldComputed(_.flowTotalChunks, _.totalChunks.getOrElse(-999))
      .transform

}
