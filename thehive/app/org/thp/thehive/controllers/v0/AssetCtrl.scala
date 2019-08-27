package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.InputChunkedAsset
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class AssetCtrl @Inject()(
    entryPoint: EntryPoint,
    implicit val db: Database
) {

  def getChunk: Action[AnyContent] =
    entryPoint("get asset chunk")
      .auth { implicit request =>
        //200, 201, 202: The chunk was accepted and correct. No need to re-upload.
        //404, 415. 500, 501: The file for which the chunk was uploaded is not supported, cancel the entire upload.
        //Anything else: Something went wrong, but try reuploading the file.
        Success(Results.NoContent)
      }

  def uploadChunk: Action[AnyContent] =
    entryPoint("upload asset chunk")
      .extract("chunk", FieldsParser[InputChunkedAsset])
      .extract("data", FieldsParser.file.on("file"))
      .auth { implicit request =>
        val inputChunkedAsset: InputChunkedAsset = request.body("chunk")
        val dataChunk: FFile = request.body("data")

        Success(Results.Ok("lol"))
      }
}
