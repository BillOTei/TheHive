package org.thp.thehive.services

import java.io.InputStream
import java.nio.file.{Files, Paths}

import org.specs2.execute.Result
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.services.StreamUtils
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.annotation.tailrec
import scala.util.Try

class AttachmentSrvTest extends PlaySpecification with StreamUtils {
  val dummyUserSrv                      = DummyUserSrv()
  implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], authContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val db: Database  = app.instanceOf[Database]
    val attachmentSrv = app.instanceOf[AttachmentSrv]

    s"[$name] attachment service" should {
      "create and stream properly a chunked attachment" in {
        val f1          = Paths.get("../build.sbt")
        lazy val f2     = Paths.get("build.sbt")
        val filePath    = if (Files.exists(f1)) f1 else f2
        val totalChunks = 10
        val is = (for (i <- 1 to totalChunks) yield i)
          .map(_ => Files.newInputStream(filePath))
          .reduceLeft(_ ++ _)

        db.tryTransaction(
          implicit graph =>
            attachmentSrv.create(Attachment("test", Files.size(filePath), "application/octet-stream", Nil, "", Some(totalChunks), Some(totalChunks)))
        ) must beSuccessfulTry.which(attachment => {
          Result.foreach(1 to totalChunks) { i =>
            db.tryTransaction { implicit graph =>
              attachmentSrv.createChunk(attachment, (i, Files.readAllBytes(filePath)))
            } must beSuccessfulTry
          }

          db.tryTransaction { implicit graph =>
            attachmentSrv.get(attachment).update("remainingChunks" -> Some(0))
          } must beSuccessfulTry.which(updatedAttachment => {
            val finalStream = attachmentSrv.streamChunks(updatedAttachment)

            streamCompare(finalStream, is) must beTrue
          })
        })
      }
    }
  }

  @tailrec
  private def streamCompare(is1: InputStream, is2: InputStream): Boolean = {
    val n1 = is1.read()
    val n2 = is2.read()
    if (n1 == -1 || n2 == -1) n1 == n2
    else (n1 == n2) && streamCompare(is1, is2)
  }
}
