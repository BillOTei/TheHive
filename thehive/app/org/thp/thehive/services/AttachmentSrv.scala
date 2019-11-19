package org.thp.thehive.services

import java.io.InputStream
import java.nio.file.Files

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{StorageSrv, StreamUtils, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.models.Attachment
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.util.Try

@Singleton
class AttachmentSrv @Inject()(configuration: Configuration, storageSrv: StorageSrv)(implicit db: Database)
    extends VertexSrv[Attachment, AttachmentSteps]
    with StreamUtils {

  lazy val logger = Logger(s"${getClass.getName}")

  val hashers = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AttachmentSteps = new AttachmentSteps(raw)

  def create(file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] = {
    val hs = hashers.fromPath(file.filepath)
    val id = hs.head.toString
    val is = Files.newInputStream(file.filepath)
    val result =
      storageSrv.saveBinary(id, is).flatMap(_ => createEntity(Attachment(file.filename, Files.size(file.filepath), file.contentType, hs, id)))
    is.close()
    result
  }

  def create(filename: String, contentType: String, data: Array[Byte])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary(id, data).flatMap(_ => createEntity(Attachment(filename, data.length.toLong, contentType, hs, id)))
  }

  /**
    * Stores a chunk thanks to StorageSrv
    * @param attachment the attachment the chunk belongs to
    * @param chunk the chunk of data to persist
    * @return
    */
  def createChunk(
      attachment: Attachment with Entity,
      chunk: (Int, Array[Byte])
  )(implicit graph: Graph): Try[Unit] = {
    val (pos, data) = chunk
    val id          = chunkId(attachment, pos)
    storageSrv.saveBinary(id, data)
  }

  def chunkExists(attachment: Attachment with Entity, pos: Int): Boolean = storageSrv.exists(chunkId(attachment, pos))

  def chunkId(attachment: Attachment with Entity, position: Int) = s"${attachment.attachmentId}_$position"

  override def get(idOrAttachmentId: String)(implicit graph: Graph): AttachmentSteps =
    if (db.isValidId(idOrAttachmentId)) getByIds(idOrAttachmentId)
    else initSteps.getByAttachmentId(idOrAttachmentId)

  def source(attachment: Attachment with Entity): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => stream(attachment))

  def stream(attachment: Attachment with Entity): InputStream = storageSrv.loadBinary(attachment._id)

  def cascadeRemove(attachment: Attachment with Entity)(implicit graph: Graph): Try[Unit] =
    // TODO handle Storage data removal
    Try(get(attachment).remove())

  /**
    * Gets a chunked attachment if fully uploaded
    * @param attachment the payload to retrieve
    * @return
    */
  def streamChunks(attachment: Attachment with Entity): Try[InputStream] = {
    if (attachment.remainingChunks.isDefined && attachment.remainingChunks.get > 0)
      logger.warn(s"Trying to fetch a non fully uploaded attachment $attachment")

    val l = for {
      remainingChunks <- attachment.remainingChunks.toStream
      if remainingChunks <= 0
      totalChunks <- attachment.totalChunks.toStream
      chunkPos    <- (1 to totalChunks).toStream
    } yield storageSrv.loadBinary(chunkId(attachment, chunkPos))

    Try(l.reduceLeft(_ ++ _))
  }
}

@EntitySteps[Attachment]
class AttachmentSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Attachment](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): AttachmentSteps = new AttachmentSteps(newRaw)
  override def newInstance(): AttachmentSteps                             = new AttachmentSteps(raw.clone())

  def getByAttachmentId(attachmentId: String): AttachmentSteps = this.has("attachmentId", attachmentId)
}
