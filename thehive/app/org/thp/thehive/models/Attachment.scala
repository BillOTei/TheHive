package org.thp.thehive.models

import org.thp.scalligraph.VertexEntity
import org.thp.scalligraph.models.{DefineIndex, IndexType}
import org.thp.scalligraph.utils.Hash

@DefineIndex(IndexType.unique, "attachmentId")
@VertexEntity
case class Attachment(
    name: String,
    size: Long,
    contentType: String,
    hashes: Seq[Hash],
    attachmentId: String,
    remainingChunks: Option[Int] = None,
    totalChunks: Option[Int] = None
)
