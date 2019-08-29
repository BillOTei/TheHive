package org.thp.thehive.controllers.v0

import java.nio.file.Files

import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.InputChunkedAsset
import org.thp.thehive.services.AttachmentSrv
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class AssetCtrl @Inject()(
    entryPoint: EntryPoint,
    implicit val db: Database,
    attachmentSrv: AttachmentSrv
) {

  import AttachmentConversion._

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
                  .has(Key("attachmentId"), P.eq(inputChunkedAsset.flowIdentifier))
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

  def uploadChunk: Action[AnyContent] =
    entryPoint("upload asset chunk")
      .extract("chunk", FieldsParser[InputChunkedAsset])
      .extract("data", FieldsParser.file.on("file"))
      .auth { implicit request =>
        val inputChunkedAsset: InputChunkedAsset = request.body("chunk")
        val dataChunk: FFile                     = request.body("data")

        for {
          attachment <- db.tryTransaction(
            implicit graph =>
              attachmentSrv
                .initSteps
                .has(Key("attachmentId"), P.eq(inputChunkedAsset.flowIdentifier))
                .getOrFail()
                .orElse(attachmentSrv.create(inputChunkedAsset))
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
          Results.Created(Json.toJson(toOutputChunkAsset(updatedAttachment)))
        }

      }
}
