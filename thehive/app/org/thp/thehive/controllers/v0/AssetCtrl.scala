package org.thp.thehive.controllers.v0

import java.nio.file.Files

import akka.stream.scaladsl.StreamConverters
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputChunkedAsset
import org.thp.thehive.services.AttachmentSrv
import play.api.Logger
import play.api.http.HttpEntity
import play.api.mvc._

import scala.util.Success

@Singleton
class AssetCtrl @Inject()(
    entryPoint: EntryPoint,
    implicit val db: Database,
    attachmentSrv: AttachmentSrv
) {

  lazy val logger = Logger(s"${getClass.getName}")

  def getChunk: Action[AnyContent] =
    entryPoint("get asset chunk")
      .extract("chunk", FieldsParser[InputChunkedAsset])
      .auth { implicit request =>
        //200, 201, 202: The chunk was accepted and correct. No need to re-upload.
        //404, 415. 500, 501: The file for which the chunk was uploaded is not supported, cancel the entire upload.
        //Anything else: Something went wrong, but try re-uploading the file.
        val inputChunkedAsset: InputChunkedAsset = request.body("chunk")

        Success(
          db.roTransaction(
              implicit graph =>
                attachmentSrv
                  .initSteps
                  .has("attachmentId", inputChunkedAsset.flowIdentifier)
                  .headOption()
            )
            .fold(Results.PreconditionFailed) { attachment =>
              if (attachmentSrv.chunkExists(attachment, inputChunkedAsset.flowChunkNumber)) {
                logger.warn(s"Chunk existing! Make sure it is the case $attachment $inputChunkedAsset")

                Results.Ok
              } else Results.PartialContent
            }
        )
      }

  def getAttachment(attachmentId: String): Action[AnyContent] =
    entryPoint("get attachment")
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          attachment <- attachmentSrv
            .get(attachmentId)
            .getOrFail()
          is <- attachmentSrv.streamChunks(attachment)
          s = StreamConverters.fromInputStream(() => is)
        } yield Result(
          header = ResponseHeader(200, Map("x-filename" -> attachment.name)),
          body = HttpEntity.Streamed(s, Some(attachment.size), Some(attachment.contentType))
        )
      }

  def uploadChunk: Action[AnyContent] =
    entryPoint("upload asset chunk")
      .extract("chunk", FieldsParser[InputChunkedAsset])
      .extract("data", FieldsParser.file.on("file"))
      .auth { implicit request =>
        val inputChunkedAsset: InputChunkedAsset = request.body("chunk")
        val dataChunk: FFile                     = request.body("data")
        val attach                               = inputChunkedAsset.toAttachment.copy(contentType = dataChunk.contentType)

        for {
          attachment <- db.tryTransaction(
            implicit graph =>
              attachmentSrv
                .initSteps
                .has("attachmentId", inputChunkedAsset.flowIdentifier)
                .getOrFail()
                .orElse(attachmentSrv.createEntity(attach))
          )
          _ <- db.tryTransaction(
            implicit graph => attachmentSrv.createChunk(attachment, (inputChunkedAsset.flowChunkNumber, Files.readAllBytes(dataChunk.filepath)))
          )
          updatedAttachment <- db.tryTransaction(
            implicit graph =>
              attachmentSrv
                .get(attachment)
                .update("remainingChunks" -> attachment.remainingChunks.map(_ - 1).orElse(Some(inputChunkedAsset.flowTotalChunks - 1)))
          )
        } yield {
          updatedAttachment.remainingChunks.foreach { i =>
            if (i <= 0)
              logger.info(
                s"All Attachment chunks for ${updatedAttachment.remainingChunks} ${updatedAttachment.name} ${updatedAttachment._id} were created"
              )
          }
          Results.Created(chunkAssetOutput.toOutput(updatedAttachment).toJson)
        }

      }
}
