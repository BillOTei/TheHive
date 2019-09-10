package org.thp.thehive.controllers.v0

import java.util.Date

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import io.scalaland.chimney.dsl._
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0._
import org.thp.thehive.models.{DatabaseBuilder, Permissions, RichObservable}
import org.thp.thehive.services.{CaseSrv, ObservableSrv}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

case class TestAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    follow: Boolean,
    customFields: Set[OutputCustomFieldValue] = Set.empty,
    caseTemplate: Option[String] = None
)

object TestAlert {

  def apply(outputAlert: OutputAlert): TestAlert =
    outputAlert.into[TestAlert].transform
}

class AlertCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val alertCtrl: AlertCtrl = app.instanceOf[AlertCtrl]
    val db                   = app.instanceOf[Database]

    "create an alert" in {
      val now = new Date()
      val outputCustomFields = Set(
        OutputCustomFieldValue("string1", "string custom field", "string", Some("string custom field")),
        OutputCustomFieldValue("float1", "float custom field", "float", Some("42.0"))
      )
      val inputCustomFields = Seq(
        InputCustomFieldValue("float1", Some(42)),
        InputCustomFieldValue("string1", Some("string custom field"))
      )
      val inputObservables =
        Seq(
          InputObservable(dataType = "ip", data = Seq("127.0.0.1"), message = Some("localhost"), tlp = Some(1), tags = Set("here")),
          InputObservable(
            dataType = "file",
            data = Seq("bye.txt;text/plain;bGVsbG8gd84ybGQgIQ=="),
            message = Some("coucou"),
            tlp = Some(1),
            tags = Set("welcome", "message")
          )
        )
      val outputObservables = Seq(
        TestObservable(dataType = "ip", data = Some("127.0.0.1"), message = Some("localhost"), tlp = 1, tags = Set("here")),
        TestObservable(
          dataType = "file",
          attachment = Some(
            OutputAttachment(
              "bye.txt",
              Seq(
                "807a7624d1c102c7e59b550b79e015375c0281ab4d3c5869aa9c095b7bdc1738",
                "813ccaed59052b1c17291544d6f7996027d1e401",
                "2470e9079d265e65a13ae762583cb787"
              ),
              13,
              "text/plain",
              "807a7624d1c102c7e59b550b79e015375c0281ab4d3c5869aa9c095b7bdc1738"
            )
          ),
          message = Some("coucou"),
          tlp = 1,
          tags = Set("welcome", "message")
        )
      )
      val request = FakeRequest("POST", "/api/v0/alert")
        .withJsonBody(
          Json
            .toJson(
              InputAlert(
                `type` = "test",
                source = "alert_creation_test",
                sourceRef = "#1",
                externalLink = None,
                title = "alert title (create alert test)",
                description = "alert description (create alert test)",
                severity = Some(2),
                date = now,
                tags = Set("tag1", "tag2"),
                flag = Some(false),
                tlp = Some(1),
                pap = Some(3),
                customFieldValue = inputCustomFields
              )
            )
            .as[JsObject] +
            ("caseTemplate" -> JsString("spam")) +
            ("artifacts"    -> Json.toJson(inputObservables))
        )
        .withHeaders("user" -> "user1")

      val result = alertCtrl.create(request)
      status(result) should equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultAlert       = contentAsJson(result)
      val resultAlertOutput = resultAlert.as[OutputAlert]
      val expected = TestAlert(
        `type` = "test",
        source = "alert_creation_test",
        sourceRef = "#1",
        title = "alert title (create alert test)",
        description = "alert description (create alert test)",
        severity = 2,
        date = now,
        tags = Set("tag1", "tag2"),
        flag = false,
        tlp = 1,
        pap = 3,
        status = "New",
        follow = true,
        customFields = outputCustomFields,
        caseTemplate = Some("spam")
      )

      TestAlert(resultAlertOutput) shouldEqual expected
      resultAlertOutput.artifacts.map(TestObservable.apply) should containTheSameElementsAs(outputObservables)
    }

    "get an alert" in {
      val request = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref1")
        .withHeaders("user" -> "user3")
      val result = alertCtrl.get("testType;testSource;ref1")(request)
      status(result) should equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultAlert       = contentAsJson(result)
      val resultAlertOutput = resultAlert.as[OutputAlert]
      val expected = TestAlert(
        `type` = "testType",
        source = "testSource",
        sourceRef = "ref1",
        title = "alert#1",
        description = "description of alert #1",
        severity = 2,
        date = new Date(1555359572000L),
        tags = Set("alert", "test"),
        flag = false,
        tlp = 2,
        pap = 2,
        status = "New",
        follow = true,
        customFields = Set(OutputCustomFieldValue("integer1", "integer custom field", "integer", Some("42"))),
        caseTemplate = None
      )

      TestAlert(resultAlertOutput) shouldEqual expected
      resultAlertOutput.artifacts.map(TestObservable.apply) shouldEqual Seq(
        TestObservable(
          dataType = "domain",
          data = Some("h.fr"),
          tlp = 3,
          tags = Set("testDomain"),
          ioc = true,
          message = Some("Some weird domain")
        )
      )

    }

    "update an alert" in {
      val request = FakeRequest("PATCH", "/api/v0/alert/testType;testSource;ref2")
        .withJsonBody(
          Json.obj(
            "tlp" -> 3
          )
        )
        .withHeaders("user" -> "user1")
      val result = alertCtrl.update("testType;testSource;ref2")(request)
      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultAlert       = contentAsJson(result)
      val resultAlertOutput = resultAlert.as[OutputAlert]
      resultAlertOutput.tlp must beEqualTo(3)
    }

    "mark an alert as read/unread" in {
      val request1 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
        .withHeaders("user" -> "user1")
      val result1 = alertCtrl.get("testType;testSource;ref3")(request1)
      status(result1) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")
      contentAsJson(result1).as[OutputAlert].status must beEqualTo("New")

      val request2 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/markAsRead")
        .withHeaders("user" -> "user1")
      val result2 = alertCtrl.markAsRead("testType;testSource;ref3")(request2)
      status(result2) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
        .withHeaders("user" -> "user1")
      val result3 = alertCtrl.get("testType;testSource;ref3")(request3)
      status(result3) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")
      contentAsJson(result3).as[OutputAlert].status must beEqualTo("Ignored")

      val request4 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/markAsUnread")
        .withHeaders("user" -> "user1")
      val result4 = alertCtrl.markAsUnread("testType;testSource;ref3")(request4)
      status(result4) should equalTo(204).updateMessage(s => s"$s\n${contentAsString(result4)}")

      val request5 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
        .withHeaders("user" -> "user1")
      val result5 = alertCtrl.get("testType;testSource;ref3")(request5)
      status(result5) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result5)}")
      contentAsJson(result5).as[OutputAlert].status must beEqualTo("New")
    }

    "follow/unfollow an alert" in {
      val request1 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
        .withHeaders("user" -> "user1")
      val result1 = alertCtrl.get("testType;testSource;ref3")(request1)
      status(result1) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")
      contentAsJson(result1).as[OutputAlert].follow must beTrue

      val request2 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/unfollow")
        .withHeaders("user" -> "user1")
      val result2 = alertCtrl.unfollowAlert("testType;testSource;ref3")(request2)
      status(result2) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
        .withHeaders("user" -> "user1")
      val result3 = alertCtrl.get("testType;testSource;ref3")(request3)
      status(result3) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")
      contentAsJson(result3).as[OutputAlert].follow must beFalse

      val request4 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/follow")
        .withHeaders("user" -> "user1")
      val result4 = alertCtrl.followAlert("testType;testSource;ref3")(request4)
      status(result4) should equalTo(204).updateMessage(s => s"$s\n${contentAsString(result4)}")

      val request5 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
        .withHeaders("user" -> "user1")
      val result5 = alertCtrl.get("testType;testSource;ref3")(request5)
      status(result5) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result5)}")
      contentAsJson(result5).as[OutputAlert].follow must beTrue
    }

    "create a case from an alert" in {
      val request1 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref5/createCase")
        .withHeaders("user" -> "user1")
      val result1 = alertCtrl.createCase("testType;testSource;ref5")(request1)
      status(result1) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val resultCase       = contentAsJson(result1)
      val resultCaseOutput = resultCase.as[OutputCase]

      val expected = TestCase(
        caseId = resultCaseOutput.caseId,
        title = "[SPAM] alert#5",
        description = "description of alert #5",
        severity = 2,
        startDate = resultCaseOutput.startDate,
        endDate = None,
        flag = false,
        tlp = 2,
        pap = 2,
        status = "Open",
        tags = Set("alert", "test", "spam", "src:mail"),
        summary = None,
        owner = None,
        customFields = Set(
          OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", None),
          OutputCustomFieldValue("string1", "string custom field", "string", Some("string1 custom field"))
        ),
        stats = Json.obj()
      )

      TestCase(resultCaseOutput) must_=== expected
      val observables = db.roTransaction { implicit graph =>
        val authContext = mock[AuthContext]
        authContext.organisation returns "cert"
        app.instanceOf[CaseSrv].get(resultCaseOutput._id).observables(authContext).richObservable.toList
      }
      observables must contain(
        exactly(
          beLike[RichObservable] {
            case RichObservable(obs, tpe, Some(data), None, tags, _) if tpe.name == "domain" && data.data == "c.fr" => ok
          },
          beLike[RichObservable] {
            case RichObservable(obs, tpe, None, Some(attachment), tags, _) if tpe.name == "file" && attachment.name == "hello.txt" => ok
          }
        )
      )
    }

    "merge an alert with a case" in {
      val request1 = FakeRequest("POST", "/api/v0/alert/test;alert_creation_test;#1/merge/#1")
        .withHeaders("user" -> "user1", "X-Organisation" -> "cert")
      val result1 = alertCtrl.mergeWithCase("test;alert_creation_test;#1", "#1")(request1)
      status(result1) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val resultCase       = contentAsJson(result1)
      val resultCaseOutput = resultCase.as[OutputCase]

      resultCaseOutput.description.contains("Merged with alert ##1") must beTrue

      db.roTransaction { implicit graph =>
        val observables = app
          .instanceOf[CaseSrv]
          .get("#1")
          .observables(DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all).getSystemAuthContext)
          .toList

        observables.find(_.message.contains("coucou")) must beSome
      }
    }

    "delete an alert" in db.roTransaction { implicit graph =>
      val observableSrv = app.instanceOf[ObservableSrv]

      observableSrv
        .initSteps
        .has(Key("message"), P.eq("coucou"))
        .alert
        .getOrFail() must beSuccessfulTry

      val request1 = FakeRequest("DELETE", "/api/v0/alert/test;alert_creation_test;#1")
        .withHeaders("user" -> "user1", "X-Organisation" -> "cert")
      val result1 = alertCtrl.delete("test;alert_creation_test;#1")(request1)

      status(result1) must equalTo(204)
      db.roTransaction(
        graph =>
          observableSrv
            .initSteps(graph)
            .has(Key("message"), P.eq("coucou"))
            .alert
            .getOrFail() must beFailedTry
      )
    }
  }
}
