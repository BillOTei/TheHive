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
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.{StorageSrv, VertexSrv}
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.models.Attachment
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.util.Try

@Singleton
class AttachmentSrv @Inject()(configuration: Configuration, storageSrv: StorageSrv)(implicit db: Database)
    extends VertexSrv[Attachment, AttachmentSteps] {

  lazy val logger = Logger(s"${getClass.getName}")

  val hashers = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AttachmentSteps = new AttachmentSteps(raw)

  def create(file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] = {
    val hs     = hashers.fromPath(file.filepath)
    val id     = hs.head.toString
    val is     = Files.newInputStream(file.filepath)
    val result = storageSrv.saveBinary(id, is).flatMap(_ => create(Attachment(file.filename, Files.size(file.filepath), file.contentType, hs, id)))
    is.close()
    result
  }

  def create(filename: String, contentType: String, data: Array[Byte])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary(id, data).flatMap(_ => create(Attachment(filename, data.length.toLong, contentType, hs, id)))
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
    val id          = s"${attachment._id}_$pos"
    storageSrv.saveBinary(id, data)
  }

  def source(attachment: Attachment with Entity)(implicit graph: Graph): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => stream(attachment))

  def stream(attachment: Attachment with Entity): InputStream = storageSrv.loadBinary(attachment._id)

  def cascadeRemove(attachment: Attachment with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    // TODO handle Storage data removal
    Try(get(attachment).remove())

}

@EntitySteps[Attachment]
class AttachmentSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Attachment, AttachmentSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): AttachmentSteps = new AttachmentSteps(raw)
}
